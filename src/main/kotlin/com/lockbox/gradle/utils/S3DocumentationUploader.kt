package com.lockbox.gradle.utils

import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.http.HttpStatusCode
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.retries.AdaptiveRetryStrategy
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * S3 Documentation Uploader with intelligent rate limiting and retry logic.
 * 
 * This uploader handles S3 rate limiting (503 SlowDown errors) by:
 * - Using AWS SDK's adaptive retry mode
 * - Implementing exponential backoff with jitter
 * - Respecting Retry-After headers from AWS
 * - Controlling concurrent uploads with a semaphore
 * 
 * **Usage:**
 * ```kotlin
 * S3DocumentationUploader(
 *     bucket = "my-docs-bucket",
 *     s3Prefix = "docs/v1.0",
 *     sourceDirectory = file("build/docs"),
 *     cacheControl = "max-age=3600",
 *     logger = logger
 * ).use { uploader ->
 *     uploader.upload()
 * }
 * ```
 * 
 * @property bucket S3 bucket name
 * @property s3Prefix S3 key prefix for uploads
 * @property sourceDirectory Local directory to upload from
 * @property cacheControl Cache-Control header value
 * @property deleteOthers If true, deletes remote files not present locally (sync behavior)
 * @property maxConcurrentRequests Maximum number of concurrent S3 upload requests
 * @property maxRetries Maximum number of retry attempts for failed uploads
 * @property baseRetryDelayMs Base delay in milliseconds for exponential backoff
 * @property maxRetryDelayMs Maximum delay in milliseconds for exponential backoff
 * @property jitterMs Random jitter in milliseconds to add to retry delays
 * @property logger Gradle logger for progress and error reporting
 */
class S3DocumentationUploader(
    private val bucket: String,
    private val s3Prefix: String,
    private val sourceDirectory: File,
    private val cacheControl: String,
    private val deleteOthers: Boolean = false,
    private val maxConcurrentRequests: Int = 5,
    private val maxRetries: Int = 5,
    private val baseRetryDelayMs: Long = 2000L,
    private val maxRetryDelayMs: Long = 60000L,
    private val jitterMs: Long = 1000L,
    private val logger: Logger
) : Closeable {
    
    private val semaphore = Semaphore(maxConcurrentRequests)
    private val successCount = AtomicInteger(0)
    private val failureCount = AtomicInteger(0)
    private val failureMessages = mutableListOf<String>()
    
    private val s3Client: S3Client by lazy {
        S3Client.builder()
            .region(Region.US_EAST_1)
            .overrideConfiguration(
                ClientOverrideConfiguration.builder()
                    .retryStrategy(
                        // Use adaptive retry strategy for intelligent rate limit handling
                        // This strategy dynamically adjusts backoff based on 503 responses
                        AdaptiveRetryStrategy.builder()
                            .maxAttempts(maxRetries)
                            .build()
                    )
                    .build()
            )
            .build()
    }
    
    /**
     * Result of an upload operation.
     */
    sealed class UploadResult {
        /**
         * Upload completed successfully.
         * 
         * @property uploadedCount Number of files uploaded
         * @property deletedCount Number of remote files deleted (sync mode)
         */
        data class Success(
            val uploadedCount: Int,
            val deletedCount: Int = 0
        ) : UploadResult()
        
        /**
         * Upload failed with errors.
         * 
         * @property successCount Number of files successfully uploaded before failure
         * @property failureCount Number of files that failed to upload
         * @property errors List of error messages
         */
        data class Failure(
            val successCount: Int,
            val failureCount: Int,
            val errors: List<String>
        ) : UploadResult()
    }
    
    /**
     * Uploads all files from the source directory to S3.
     * 
     * Implements a three-phase upload strategy:
     * 1. Upload HTML files with explicit content-type
     * 2. Upload all other files with auto-detected content-type
     * 3. Delete remote files not present locally (if deleteOthers is true)
     * 
     * @return UploadResult indicating success or failure with details
     * @throws GradleException if upload fails after all retries
     */
    fun upload(): UploadResult {
        if (!sourceDirectory.exists() || !sourceDirectory.isDirectory) {
            throw GradleException("Source directory does not exist: ${sourceDirectory.absolutePath}")
        }
        
        logger.lifecycle("Uploading from ${sourceDirectory.absolutePath} to s3://$bucket/$s3Prefix")
        logger.lifecycle("Cache-Control: $cacheControl")
        logger.lifecycle("Configuration: maxConcurrentRequests=$maxConcurrentRequests, maxRetries=$maxRetries")
        
        val allFiles = collectFiles(sourceDirectory)
        if (allFiles.isEmpty()) {
            logger.warn("No files found to upload in ${sourceDirectory.absolutePath}")
            return UploadResult.Success(uploadedCount = 0, deletedCount = 0)
        }
        
        logger.lifecycle("Found ${allFiles.size} files to upload")
        
        // Phase 1: Upload HTML files with explicit content-type
        val htmlFiles = allFiles.filter { it.extension.lowercase() == "html" }
        logger.lifecycle("Phase 1: Uploading ${htmlFiles.size} HTML files...")
        htmlFiles.forEachIndexed { index, file ->
            val key = getS3Key(file)
            uploadWithRetry(file, key, "text/html; charset=utf-8")
            logProgress(index + 1, htmlFiles.size, "Phase 1")
        }
        
        // Phase 2: Upload all other files
        val otherFiles = allFiles.filter { it.extension.lowercase() != "html" }
        logger.lifecycle("Phase 2: Uploading ${otherFiles.size} non-HTML files...")
        otherFiles.forEachIndexed { index, file ->
            val key = getS3Key(file)
            val contentType = detectContentType(file)
            uploadWithRetry(file, key, contentType)
            logProgress(index + 1, otherFiles.size, "Phase 2")
        }
        
        // Phase 3: Delete remote files not present locally (if deleteOthers is true)
        val deletedCount = if (deleteOthers) {
            logger.lifecycle("Phase 3: Checking for remote files to delete...")
            deleteRemoteOnlyFiles(allFiles)
        } else {
            0
        }
        
        val successes = successCount.get()
        val failures = failureCount.get()
        
        return if (failures > 0) {
            val errors = synchronized(failureMessages) { failureMessages.toList() }
            logger.error("✗ Upload completed with errors: $successes successful, $failures failed")
            throw GradleException(
                "Upload failed: $failures files failed to upload after $maxRetries retries. " +
                "First error: ${errors.firstOrNull() ?: "Unknown error"}"
            )
        } else {
            logger.lifecycle("✓ Upload completed successfully: $successes files uploaded" + 
                if (deletedCount > 0) ", $deletedCount files deleted" else "")
            UploadResult.Success(uploadedCount = successes, deletedCount = deletedCount)
        }
    }
    
    /**
     * Collects all files recursively from the source directory.
     */
    private fun collectFiles(directory: File): List<File> {
        return directory.walkTopDown()
            .filter { it.isFile }
            .toList()
    }
    
    /**
     * Generates the S3 key for a local file.
     */
    private fun getS3Key(file: File): String {
        val relativePath = file.relativeTo(sourceDirectory).path
        return "$s3Prefix/$relativePath".replace("\\", "/")
    }
    
    /**
     * Detects content type for a file using the platform's file type detection.
     */
    private fun detectContentType(file: File): String {
        return runCatching { Files.probeContentType(file.toPath()) }
            .getOrNull() ?: "application/octet-stream"
    }
    
    /**
     * Logs progress at percentage milestones (25%, 50%, 75%, 100%).
     * 
     * @param current Current number of files processed
     * @param total Total number of files to process
     * @param phase Phase name for logging context
     */
    private fun logProgress(current: Int, total: Int, phase: String) {
        if (total == 0) return
        val percentage = (current * 100) / total
        // Log at 25%, 50%, 75%, and 100% milestones
        if (percentage % 25 == 0 && percentage > 0) {
            logger.lifecycle("  $phase: $current/$total files ($percentage%)")
        }
    }
    
    /**
     * Uploads a single file with retry logic and exponential backoff.
     */
    private fun uploadWithRetry(file: File, key: String, contentType: String) {
        semaphore.acquire()
        try {
            var attempt = 0
            var lastException: Exception? = null
            
            while (attempt < maxRetries) {
                try {
                    uploadFile(file, key, contentType)
                    successCount.incrementAndGet()
                    return
                } catch (e: S3Exception) {
                    lastException = e
                    attempt++
                    
                    if (e.statusCode() == HttpStatusCode.SERVICE_UNAVAILABLE || 
                        e.awsErrorDetails()?.errorCode() == "SlowDown") {
                        
                        if (attempt < maxRetries) {
                            val delay = calculateRetryDelay(attempt, e)
                            logger.warn("⚠ Rate limited uploading $key (attempt $attempt/$maxRetries). " +
                                "Waiting ${delay.toMillis()}ms before retry...")
                            Thread.sleep(delay.toMillis())
                        }
                    } else {
                        // Non-retryable error
                        throw e
                    }
                } catch (e: SdkException) {
                    lastException = e
                    attempt++
                    
                    if (attempt < maxRetries) {
                        val delay = calculateRetryDelay(attempt, null)
                        logger.warn("⚠ Error uploading $key (attempt $attempt/$maxRetries): ${e.message}. " +
                            "Waiting ${delay.toMillis()}ms before retry...")
                        Thread.sleep(delay.toMillis())
                    }
                }
            }
            
            // All retries exhausted
            failureCount.incrementAndGet()
            val errorMessage = "Failed to upload $key after $maxRetries attempts: ${lastException?.message}"
            synchronized(failureMessages) {
                failureMessages.add(errorMessage)
            }
            logger.error("✗ $errorMessage")
            
        } finally {
            semaphore.release()
        }
    }
    
    /**
     * Uploads a single file to S3.
     */
    private fun uploadFile(file: File, key: String, contentType: String) {
        val putRequest = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(contentType)
            .cacheControl(cacheControl)
            .build()
        
        s3Client.putObject(putRequest, RequestBody.fromFile(file))
    }
    
    /**
     * Calculates retry delay with exponential backoff and jitter.
     * Respects Retry-After header if present in the exception.
     * 
     * @param attempt The current attempt number (0-indexed)
     * @param exception Optional S3Exception that may contain Retry-After header
     * @return Duration to wait before the next retry
     */
    private fun calculateRetryDelay(attempt: Int, exception: S3Exception?): Duration {
        // Check for Retry-After header
        if (exception != null) {
            val retryAfterOptional = exception.awsErrorDetails()
                ?.sdkHttpResponse()
                ?.firstMatchingHeader("Retry-After")
            
            if (retryAfterOptional?.isPresent == true) {
                val value = retryAfterOptional.get()
                runCatching {
                    // Retry-After can be in seconds
                    val seconds = value.toLong()
                    return Duration.ofSeconds(seconds)
                }
                // If parsing fails, fall through to exponential backoff
            }
        }
        
        // Exponential backoff: min(maxDelay, baseDelay * 2^attempt) + random jitter
        val exponentialDelay = min(
            maxRetryDelayMs.toDouble(),
            baseRetryDelayMs * 2.0.pow(attempt.toDouble())
        ).toLong()
        
        val jitter = Random.nextLong(0, jitterMs)
        return Duration.ofMillis(exponentialDelay + jitter)
    }
    
    /**
     * Deletes files in S3 that don't exist locally (sync behavior).
     * 
     * @param localFiles List of local files being uploaded
     * @return Number of remote files deleted
     */
    private fun deleteRemoteOnlyFiles(localFiles: List<File>): Int {
        val localKeys = localFiles.map { getS3Key(it) }.toSet()
        var deletedCount = 0
        
        runCatching {
            val listRequest = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(s3Prefix)
                .build()
            
            val paginator = s3Client.listObjectsV2Paginator(listRequest)
            
            for (response in paginator) {
                val remoteKeys = response.contents().map { it.key() }
                val keysToDelete = remoteKeys.filter { it !in localKeys }
                
                if (keysToDelete.isNotEmpty()) {
                    keysToDelete.chunked(1000).forEach { batch ->
                        val deleteRequest = DeleteObjectsRequest.builder()
                            .bucket(bucket)
                            .delete { delete ->
                                delete.objects(batch.map { key ->
                                    ObjectIdentifier.builder().key(key).build()
                                })
                            }
                            .build()
                        
                        s3Client.deleteObjects(deleteRequest)
                        deletedCount += batch.size
                        logger.lifecycle("  Phase 3: Deleted $deletedCount files so far...")
                    }
                }
            }
        }.onFailure { e ->
            logger.warn("Warning: Failed to delete remote files: ${e.message}")
        }
        
        return deletedCount
    }
    
    /**
     * Closes the S3 client and releases resources.
     */
    override fun close() {
        s3Client.close()
    }
}

