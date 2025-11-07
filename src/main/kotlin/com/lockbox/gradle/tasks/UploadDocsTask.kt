package com.lockbox.gradle.tasks

import com.lockbox.gradle.utils.S3DocumentationUploader
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.File

/**
 * Uploads documentation to AWS S3 with intelligent rate limiting and retry logic.
 *
 * This task orchestrates the upload of generated documentation to an S3 bucket
 * with a sophisticated two-phase strategy:
 *
 * **Phase 1: Versioned Documentation**
 * Uploads the complete documentation for the current version with long-term caching:
 * - Cache-Control: public,max-age=31536000,immutable (1 year)
 * - Enables CDN caching and browser caching
 * - Immutable flag prevents cache invalidation
 *
 * **Phase 2: Latest Redirect**
 * Uploads the "latest" redirect with short-term caching:
 * - Cache-Control: public,max-age=60,must-revalidate (1 minute)
 * - Allows quick updates to point to new versions
 * - Must-revalidate ensures freshness checks
 *
 * **Rate Limiting & Retry Strategy:**
 * Uses [S3DocumentationUploader] with intelligent handling of S3 rate limits:
 * - AWS SDK adaptive retry strategy for dynamic backoff
 * - Exponential backoff with jitter: min(60s, 2s * 2^attempt) + random(0, 1s)
 * - Respects Retry-After headers from AWS
 * - Concurrent upload control (default: 5 concurrent requests)
 * - Automatic retry on 503 SlowDown errors
 *
 * **Configuration Cache Compatible:** Yes
 * All inputs are captured at configuration time, making this task fully compatible
 * with Gradle's configuration cache.
 *
 * **Usage:**
 * ```kotlin
 * tasks.register<UploadDocsTask>("uploadDocs") {
 *     docsBucket.set("my-docs-bucket")
 *     s3BasePrefix.set("site/lockbox-framework")
 *     projectVersion.set("1.0.0")
 *     artifactType.set("site")
 *     projectSlug.set("lockbox-framework")
 *     stagingDir.set(layout.buildDirectory.dir("docsUpload"))
 * }
 * ```
 *
 * @see com.lockbox.gradle.utils.S3DocumentationUploader
 * @author Lockbox AI Engineering
 */
abstract class UploadDocsTask : DefaultTask() {
    /**
     * The AWS S3 bucket name where documentation will be uploaded.
     * Must be configured via Gradle property, environment variable, or settings.gradle.kts.
     */
    @get:Input
    abstract val docsBucket: Property<String>

    /**
     * The S3 key prefix for documentation uploads (e.g., "site/lockbox-framework").
     * Used to organize documentation by artifact type and project.
     */
    @get:Input
    abstract val s3BasePrefix: Property<String>

    /**
     * The version of the project being uploaded.
     * Used to construct the versioned S3 path.
     */
    @get:Input
    abstract val projectVersion: Property<String>

    /**
     * The artifact type (e.g., "site", "javadoc", "reports").
     * Used for logging and documentation portal categorization.
     */
    @get:Input
    abstract val artifactType: Property<String>

    /**
     * The project slug used in documentation URLs.
     * Used for logging and documentation portal display.
     */
    @get:Input
    abstract val projectSlug: Property<String>

    /**
     * The staging directory containing prepared documentation for upload.
     * Should contain versioned and latest subdirectories.
     */
    @get:InputDirectory
    abstract val stagingDir: DirectoryProperty

    /**
     * Executes the documentation upload task.
     *
     * Performs a two-phase upload:
     * 1. Uploads versioned documentation with long-term caching
     * 2. Uploads latest redirect with short-term caching
     *
     * Handles S3 rate limiting automatically with exponential backoff and retry logic.
     *
     * @throws GradleException if AWS configuration is missing or upload fails after retries
     */
    @TaskAction
    fun upload() {
        val bucket = docsBucket.get()
        val prefix = s3BasePrefix.get()
        val version = projectVersion.get()
        val artifactTypeVal = artifactType.get()
        val projectSlugVal = projectSlug.get()
        
        requireAwsConfig(bucket)
        logger.lifecycle("Uploading documentation to s3://$bucket/$prefix/")
        
        val root = stagingDir.get().asFile
        val versionDir = root.resolve("$prefix/$version")
        val latestDir = root.resolve("$prefix/latest")
        
        try {
            logger.lifecycle("Uploading versioned documentation...")
            val versionUploader = S3DocumentationUploader(
                bucket = bucket,
                s3Prefix = "$prefix/$version",
                sourceDirectory = versionDir,
                cacheControl = "public,max-age=31536000,immutable",
                deleteOthers = true,
                logger = logger
            )
            versionUploader.use { it.upload() }
            
            logger.lifecycle("Uploading latest redirect...")
            val latestUploader = S3DocumentationUploader(
                bucket = bucket,
                s3Prefix = "$prefix/latest",
                sourceDirectory = latestDir,
                cacheControl = "public,max-age=60,must-revalidate",
                deleteOthers = false,
                logger = logger
            )
            latestUploader.use { it.upload() }
            
            logger.lifecycle("Documentation published successfully!")
            logger.lifecycle("View at: https://engineering-docs.lockboxai.com/$artifactTypeVal/$projectSlugVal/latest/")
            
        } catch (e: Exception) {
            throw GradleException("Failed to upload documentation: ${e.message}", e)
        }
    }

    /**
     * Validates that AWS S3 bucket configuration is available.
     *
     * @param bucket The S3 bucket name to validate
     * @throws IllegalStateException if bucket is blank or not configured
     */
    private fun requireAwsConfig(bucket: String) {
        check(bucket.isNotBlank()) {
            """
            Missing docs bucket configuration.
            Set via: -Pdocs.bucket=bucket-name
            Or environment variable: DOCS_BUCKET
            """.trimIndent()
        }
    }
}
