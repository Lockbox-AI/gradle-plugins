package com.lockbox.gradle.aws

import org.gradle.api.logging.Logger
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.retries.StandardRetryStrategy
import software.amazon.awssdk.services.codeartifact.CodeartifactClient
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.sts.StsClient
import java.time.Duration

/**
 * Factory for creating AWS SDK clients with build-tool-appropriate configuration.
 *
 * Provides centralized creation of AWS service clients with:
 * - Default credential provider chain (SSO, profiles, env vars, IAM roles)
 * - Appropriate timeouts for build operations
 * - Retry strategies suitable for build tools
 * - Consistent logging and error handling
 *
 * @property logger Gradle logger for diagnostic output
 * @property region AWS region for client operations
 */
class AwsClientFactory(
    private val logger: Logger,
    private val region: String = "us-east-1"
) {
    private val awsRegion = Region.of(region)

    /**
     * Creates a CodeArtifact client with build-appropriate configuration.
     *
     * @return Configured CodeartifactClient
     */
    fun createCodeArtifactClient(): CodeartifactClient {
        logger.debug("Creating CodeArtifact client for region: $region")
        return CodeartifactClient.builder()
            .region(awsRegion)
            .overrideConfiguration(buildClientConfig())
            .build()
    }

    /**
     * Creates an STS client with build-appropriate configuration.
     *
     * @return Configured StsClient
     */
    fun createStsClient(): StsClient {
        logger.debug("Creating STS client for region: $region")
        return StsClient.builder()
            .region(awsRegion)
            .overrideConfiguration(buildClientConfig())
            .build()
    }

    /**
     * Creates an SSM client with build-appropriate configuration.
     *
     * @return Configured SsmClient
     */
    fun createSsmClient(): SsmClient {
        logger.debug("Creating SSM client for region: $region")
        return SsmClient.builder()
            .region(awsRegion)
            .overrideConfiguration(buildClientConfig())
            .build()
    }

    /**
     * Creates an SSM client with explicit credentials and build-appropriate configuration.
     *
     * @param credentialsProvider Explicit credentials provider to use
     * @return Configured SsmClient with the provided credentials
     */
    fun createSsmClientWithCredentials(credentialsProvider: software.amazon.awssdk.auth.credentials.AwsCredentialsProvider): SsmClient {
        logger.debug("Creating SSM client with explicit credentials for region: $region")
        return SsmClient.builder()
            .region(awsRegion)
            .credentialsProvider(credentialsProvider)
            .overrideConfiguration(buildClientConfig())
            .build()
    }

    /**
     * Builds client override configuration suitable for build tools.
     *
     * Configuration includes:
     * - API call timeout: 30 seconds (suitable for build operations)
     * - API call attempt timeout: 10 seconds per attempt
     * - Standard retry strategy with 3 max attempts and exponential backoff
     *
     * @return ClientOverrideConfiguration for AWS SDK clients
     */
    fun buildClientConfig(): ClientOverrideConfiguration {
        val retryStrategy = StandardRetryStrategy.builder()
            .maxAttempts(3)
            .build()
            
        return ClientOverrideConfiguration.builder()
            .apiCallTimeout(Duration.ofSeconds(30))
            .apiCallAttemptTimeout(Duration.ofSeconds(10))
            .retryStrategy(retryStrategy)
            .build()
    }
}
