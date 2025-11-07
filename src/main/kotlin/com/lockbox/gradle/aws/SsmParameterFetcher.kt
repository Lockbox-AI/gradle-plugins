package com.lockbox.gradle.aws

import org.gradle.api.logging.Logger
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest
import software.amazon.awssdk.services.sts.model.Credentials
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region

/**
 * Service for fetching SSM parameters with automatic cross-account role assumption.
 *
 * Handles:
 * - Assuming `EngineeringArtifactsParameterReader` role in artifacts account
 * - Fetching parameters from `/engineering-artifacts/` path
 * - Parsing and returning structured configuration
 * - Graceful error handling with logging
 *
 * @property logger Gradle logger for diagnostic output
 * @property region AWS region for operations
 * @property artifactsAccountId AWS account ID containing engineering artifacts
 */
class SsmParameterFetcher(
    private val logger: Logger,
    private val region: String = "us-east-1",
    private val artifactsAccountId: String? = null
) {
    private val awsRegion = Region.of(region)

    /**
     * Fetches engineering artifacts configuration from SSM Parameter Store.
     *
     * Attempts to:
     * 1. Assume the EngineeringArtifactsParameterReader role in the artifacts account
     * 2. Fetch parameters from `/engineering-artifacts/` path
     * 3. Parse and return structured configuration
     *
     * @return Map of parameter names to values, or empty map if fetch fails
     */
    fun fetchEngineeringArtifacts(): Map<String, String> {
        return try {
            logger.debug("Fetching engineering artifacts configuration from SSM")

            // Create AWS client factory for consistent client configuration
            val clientFactory = AwsClientFactory(logger, region)

            // Create STS client to assume role
            val stsClient = clientFactory.createStsClient()

            // Assume the parameter reader role
            val assumedCredentials = assumeParameterReaderRole(stsClient)
            stsClient.close()

            if (assumedCredentials == null) {
                logger.warn("Failed to assume EngineeringArtifactsParameterReader role")
                return emptyMap()
            }

            // Log credential details for debugging (without exposing secrets)
            logger.debug("Using assumed credentials with Access Key ID: ${assumedCredentials.accessKeyId().take(8)}...")

            // Create SSM client with assumed role credentials using factory
            val ssmClient = createSsmClientWithCredentials(clientFactory, assumedCredentials)

            // Fetch parameters
            val parameters = fetchParametersFromPath(ssmClient, "/engineering-artifacts/")
            ssmClient.close()

            logger.debug("Successfully fetched ${parameters.size} parameters from SSM")
            parameters
        } catch (e: Exception) {
            logger.warn("Error fetching engineering artifacts from SSM: ${e.message}")
            emptyMap()
        }
    }

    /**
     * Assumes the EngineeringArtifactsParameterReader role in the artifacts account.
     *
     * @param stsClient STS client for role assumption
     * @return Assumed role credentials, or null if assumption fails
     */
    private fun assumeParameterReaderRole(stsClient: StsClient): Credentials? {
        return try {
            if (artifactsAccountId.isNullOrBlank()) {
                logger.warn("Artifacts account ID not configured. Cannot assume EngineeringArtifactsParameterReader role.")
                return null
            }
            val roleArn = "arn:aws:iam::${artifactsAccountId}:role/EngineeringArtifactsParameterReader"
            logger.debug("Assuming EngineeringArtifactsParameterReader role: $roleArn")

            val request = AssumeRoleRequest.builder()
                .roleArn(roleArn)
                .roleSessionName("gradle-ssm-reader-${System.currentTimeMillis()}")
                .durationSeconds(900) // 15 minutes - sufficient for build
                .build()

            val response = stsClient.assumeRole(request)
            val credentials = response.credentials()
            logger.debug("Successfully assumed EngineeringArtifactsParameterReader role (expires: ${credentials.expiration()})")
            credentials
        } catch (e: Exception) {
            logger.warn("Failed to assume EngineeringArtifactsParameterReader role: ${e.message}")
            null
        }
    }

    /**
     * Creates an SSM client using assumed role credentials with proper client configuration.
     *
     * This method uses the AwsClientFactory to ensure the SSM client has proper timeouts,
     * retry strategies, and credential provider configuration. The explicit credentials
     * provider ensures the assumed role credentials take precedence over any system
     * properties or environment variables.
     *
     * @param clientFactory AWS client factory for consistent configuration
     * @param credentials Assumed role credentials
     * @return Configured SsmClient with assumed role credentials
     */
    private fun createSsmClientWithCredentials(clientFactory: AwsClientFactory, credentials: Credentials): SsmClient {
        logger.debug("Creating SSM client with assumed role credentials")
        
        val sessionCredentials = AwsSessionCredentials.create(
            credentials.accessKeyId(),
            credentials.secretAccessKey(),
            credentials.sessionToken()
        )

        val credentialsProvider = StaticCredentialsProvider.create(sessionCredentials)

        // Use the factory method to create SSM client with explicit credentials
        // This ensures proper client configuration and credential precedence
        return clientFactory.createSsmClientWithCredentials(credentialsProvider)
    }

    /**
     * Fetches all parameters from a given SSM path recursively.
     *
     * @param ssmClient SSM client for parameter retrieval
     * @param path SSM parameter path (e.g., `/engineering-artifacts/`)
     * @return Map of parameter names to values
     */
    private fun fetchParametersFromPath(ssmClient: SsmClient, path: String): Map<String, String> {
        val parameters = mutableMapOf<String, String>()

        try {
            logger.debug("Fetching parameters from SSM path: $path (recursive)")
            
            val request = GetParametersByPathRequest.builder()
                .path(path)
                .recursive(true)
                .withDecryption(false)
                .build()

            val paginator = ssmClient.getParametersByPathPaginator(request)

            var pageCount = 0
            for (response in paginator) {
                pageCount++
                logger.debug("Processing SSM response page $pageCount with ${response.parameters().size} parameters")
                response.parameters().forEach { param ->
                    parameters[param.name()] = param.value()
                    logger.debug("  - ${param.name()}")
                }
            }

            logger.debug("Successfully fetched ${parameters.size} parameters from path: $path")
        } catch (e: Exception) {
            logger.warn("Error fetching parameters from path $path: ${e.message}")
            logger.debug("Full exception details: ", e)
        }

        return parameters
    }

    /**
     * Parses SSM parameters into a structured configuration map.
     *
     * Extracts specific parameters and returns them with simplified keys.
     *
     * @param ssmParameters Raw SSM parameters map
     * @return Structured configuration map
     */
    fun parseConfiguration(ssmParameters: Map<String, String>): Map<String, String> {
        val config = mutableMapOf<String, String>()

        // Map SSM parameter names to configuration keys
        val parameterMappings = mapOf(
            "/engineering-artifacts/codeartifact/domain-owner" to "codeartifact.accountId",
            "/engineering-artifacts/codeartifact/domain-name" to "codeartifact.domain",
            "/engineering-artifacts/codeartifact/repositories/java/name" to "codeartifact.javaRepository",
            "/engineering-artifacts/codeartifact/repositories/java-snapshots/name" to "codeartifact.javaSnapshotsRepository",
            "/engineering-artifacts/gradle-cache/bucket-name" to "gradle.cacheBucket",
            "/engineering-artifacts/gradle-cache/prefix" to "gradle.cachePrefix",
            "/engineering-artifacts/docs-portal/bucket-name" to "docs.portalBucket"
        )

        parameterMappings.forEach { (ssmKey, configKey) ->
            ssmParameters[ssmKey]?.let { value ->
                config[configKey] = value
            }
        }

        return config
    }
}
