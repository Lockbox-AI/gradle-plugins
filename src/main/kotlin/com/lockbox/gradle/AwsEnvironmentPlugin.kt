package com.lockbox.gradle

import com.lockbox.gradle.aws.AwsClientFactory
import com.lockbox.gradle.aws.SsmParameterFetcher
import org.gradle.api.Plugin
import org.gradle.api.Project
import software.amazon.awssdk.services.codeartifact.CodeartifactClient
import software.amazon.awssdk.services.codeartifact.model.GetAuthorizationTokenRequest
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.model.GetCallerIdentityRequest

/**
 * Gradle plugin for AWS environment setup and configuration.
 *
 * Handles:
 * - AWS authentication using default credential provider chain
 * - Automatic STS role assumption for SSO users
 * - CodeArtifact authorization token retrieval
 * - SSM parameter fetching for engineering artifacts configuration
 * - Setting project properties for downstream use
 *
 * Configuration is read from (in priority order):
 * 1. Environment variables
 * 2. Gradle properties (gradle.properties)
 * 3. Extension configuration
 * 4. Built-in defaults
 *
 * Operates in permissive mode - logs warnings but continues if AWS services are unavailable.
 */
class AwsEnvironmentPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Create and register the extension
        val extension = project.extensions.create("awsEnvironment", AwsEnvironmentExtension::class.java)

        // Configure the plugin after project evaluation
        project.afterEvaluate {
            setupAwsEnvironment(project, extension)
        }
    }

    private fun setupAwsEnvironment(project: Project, extension: AwsEnvironmentExtension) {
        val logger = project.logger

        logger.lifecycle("Setting up AWS environment...")

        // Read configuration with proper precedence
        val config = readConfiguration(project, extension)

        try {
            // Get AWS account ID and verify credentials
            val accountId = getAwsAccountId(config, logger)
            if (accountId != null) {
                project.extensions.extraProperties["aws.accountId"] = accountId
                logger.lifecycle("AWS Account ID: $accountId")
            }

            // Assume DeveloperRole if using SSO
            if (config.enableRoleAssumption) {
                assumeDeveloperRoleIfNeeded(config, logger)
            }

            // Get CodeArtifact authorization token
            val authToken = getCodeArtifactToken(config, logger)
            if (authToken != null) {
                project.extensions.extraProperties["codeartifact.authToken"] = authToken
                logger.lifecycle("CodeArtifact authorization token retrieved")
            }

            // Fetch SSM parameters if enabled
            if (config.enableSsmFetch) {
                val ssmParams = fetchSsmParameters(config, logger)
                setSsmProperties(project, ssmParams)
            }

            // Set standard properties
            setStandardProperties(project, config)

            logger.lifecycle("AWS environment setup completed successfully")
        } catch (e: Exception) {
            if (config.failOnAwsErrors) {
                throw e
            } else {
                logger.warn("AWS environment setup encountered an error (continuing in permissive mode): ${e.message}")
            }
        }
    }

    /**
     * Reads configuration from environment variables, gradle properties, and extension.
     */
    private fun readConfiguration(project: Project, extension: AwsEnvironmentExtension): AwsConfig {
        return AwsConfig(
            codeartifactDomain = System.getenv("CODEARTIFACT_DOMAIN")
                ?: project.findProperty("aws.codeartifact.domain")?.toString()
                ?: extension.codeartifactDomain,
            codeartifactAccountId = System.getenv("CODEARTIFACT_ACCOUNT_ID")
                ?: project.findProperty("aws.codeartifact.accountId")?.toString()
                ?: extension.codeartifactAccountId,
            codeartifactRegion = System.getenv("AWS_REGION")
                ?: project.findProperty("aws.codeartifact.region")?.toString()
                ?: extension.codeartifactRegion,
            developerRoleName = System.getenv("DEVELOPER_ROLE_NAME")
                ?: project.findProperty("aws.developerRole")?.toString()
                ?: extension.developerRoleName,
            ssmParameterPath = System.getenv("SSM_PARAMETER_PATH")
                ?: project.findProperty("aws.ssm.parameterPath")?.toString()
                ?: extension.ssmParameterPath,
            enableRoleAssumption = (System.getenv("AWS_ROLE_ASSUMPTION_ENABLED")?.toBoolean()
                ?: project.findProperty("aws.roleAssumption.enabled")?.toString()?.toBoolean()
                ?: extension.enableRoleAssumption),
            enableSsmFetch = (System.getenv("AWS_SSM_FETCH_ENABLED")?.toBoolean()
                ?: project.findProperty("aws.ssm.fetch.enabled")?.toString()?.toBoolean()
                ?: extension.enableSsmFetch),
            failOnAwsErrors = (System.getenv("AWS_FAIL_ON_ERRORS")?.toBoolean()
                ?: project.findProperty("aws.failOnErrors")?.toString()?.toBoolean()
                ?: extension.failOnAwsErrors),
            javaRepository = System.getenv("JAVA_REPO_NAME")
                ?: project.findProperty("codeartifact.java.repository")?.toString()
                ?: "releases",
            javaSnapshotsRepository = System.getenv("JAVA_SNAPSHOTS_REPO_NAME")
                ?: project.findProperty("codeartifact.java.snapshots.repository")?.toString()
                ?: "snapshots"
        )
    }

    /**
     * Gets the current AWS account ID.
     */
    private fun getAwsAccountId(config: AwsConfig, logger: org.gradle.api.logging.Logger): String? {
        return try {
            val stsClient = AwsClientFactory(logger, config.codeartifactRegion).createStsClient()
            val response = stsClient.getCallerIdentity(GetCallerIdentityRequest.builder().build())
            val accountId = response.account()
            stsClient.close()
            accountId
        } catch (e: Exception) {
            logger.warn("Failed to get AWS account ID: ${e.message}")
            null
        }
    }

    /**
     * Assumes DeveloperRole if the current identity is an SSO role.
     */
    private fun assumeDeveloperRoleIfNeeded(config: AwsConfig, logger: org.gradle.api.logging.Logger) {
        try {
            val stsClient = AwsClientFactory(logger, config.codeartifactRegion).createStsClient()
            val identity = stsClient.getCallerIdentity(GetCallerIdentityRequest.builder().build())
            val arn = identity.arn()

            if (arn.contains("AWSReservedSSO_")) {
                logger.lifecycle("Detected SSO role, attempting to assume ${config.developerRoleName}...")

                val accountId = arn.split(":")[4]
                val roleArn = "arn:aws:iam::${accountId}:role/${config.developerRoleName}"

                val assumeRoleRequest = software.amazon.awssdk.services.sts.model.AssumeRoleRequest.builder()
                    .roleArn(roleArn)
                    .roleSessionName("gradle-dev-session-${System.currentTimeMillis()}")
                    .durationSeconds(3600)
                    .build()

                val assumeResponse = stsClient.assumeRole(assumeRoleRequest)
                val credentials = assumeResponse.credentials()

                // Set environment variables for subsequent AWS SDK calls
                System.setProperty("aws.accessKeyId", credentials.accessKeyId())
                System.setProperty("aws.secretAccessKey", credentials.secretAccessKey())
                System.setProperty("aws.sessionToken", credentials.sessionToken())

                logger.lifecycle("Successfully assumed ${config.developerRoleName}")
            } else {
                logger.debug("Not using SSO role, skipping role assumption")
            }

            stsClient.close()
        } catch (e: Exception) {
            logger.warn("Failed to assume DeveloperRole: ${e.message}")
        }
    }

    /**
     * Gets CodeArtifact authorization token.
     */
    private fun getCodeArtifactToken(config: AwsConfig, logger: org.gradle.api.logging.Logger): String? {
        return try {
            if (config.codeartifactDomain.isNullOrBlank()) {
                logger.warn("CodeArtifact domain not configured. Set via CODEARTIFACT_DOMAIN env var, aws.codeartifact.domain property, or extension DSL")
                return null
            }
            if (config.codeartifactAccountId.isNullOrBlank()) {
                logger.warn("CodeArtifact account ID not configured. Set via CODEARTIFACT_ACCOUNT_ID env var, aws.codeartifact.accountId property, or extension DSL")
                return null
            }

            val caClient = AwsClientFactory(logger, config.codeartifactRegion).createCodeArtifactClient()
            val request = GetAuthorizationTokenRequest.builder()
                .domain(config.codeartifactDomain)
                .domainOwner(config.codeartifactAccountId)
                .build()

            val response = caClient.getAuthorizationToken(request)
            val token = response.authorizationToken()
            caClient.close()
            token
        } catch (e: Exception) {
            logger.warn("Failed to get CodeArtifact authorization token: ${e.message}")
            null
        }
    }

    /**
     * Fetches SSM parameters for engineering artifacts.
     */
    private fun fetchSsmParameters(config: AwsConfig, logger: org.gradle.api.logging.Logger): Map<String, String> {
        return try {
            val fetcher = SsmParameterFetcher(logger, config.codeartifactRegion, config.codeartifactAccountId)
            fetcher.fetchEngineeringArtifacts()
        } catch (e: Exception) {
            logger.warn("Failed to fetch SSM parameters: ${e.message}")
            emptyMap()
        }
    }

    /**
     * Sets project properties from SSM parameters.
     */
    private fun setSsmProperties(project: Project, ssmParams: Map<String, String>) {
        val fetcher = SsmParameterFetcher(project.logger)
        val config = fetcher.parseConfiguration(ssmParams)

        config.forEach { (key, value) ->
            project.extensions.extraProperties[key] = value
            project.logger.debug("Set property $key from SSM")
        }
    }

    /**
     * Sets standard AWS properties on the project.
     */
    private fun setStandardProperties(project: Project, config: AwsConfig) {
        project.extensions.extraProperties["aws.region"] = config.codeartifactRegion
        config.codeartifactDomain?.let {
            project.extensions.extraProperties["codeartifact.domain"] = it
        }
        config.codeartifactAccountId?.let {
            project.extensions.extraProperties["codeartifact.accountId"] = it
        }
        project.extensions.extraProperties["codeartifact.javaRepository"] = config.javaRepository
        project.extensions.extraProperties["codeartifact.javaSnapshotsRepository"] = config.javaSnapshotsRepository
    }

    /**
     * Configuration data class for AWS environment.
     */
    private data class AwsConfig(
        val codeartifactDomain: String?,
        val codeartifactAccountId: String?,
        val codeartifactRegion: String,
        val developerRoleName: String,
        val ssmParameterPath: String,
        val enableRoleAssumption: Boolean,
        val enableSsmFetch: Boolean,
        val failOnAwsErrors: Boolean,
        val javaRepository: String,
        val javaSnapshotsRepository: String
    )
}

/**
 * Extension for configuring the AWS environment plugin.
 *
 * All properties can be overridden via:
 * 1. Environment variables (highest priority)
 * 2. Gradle properties in gradle.properties
 * 3. Extension configuration in build.gradle.kts
 * 4. Built-in defaults (lowest priority)
 */
open class AwsEnvironmentExtension {
    var codeartifactDomain: String? = null
    var codeartifactAccountId: String? = null
    var codeartifactRegion: String = "us-east-1"
    var developerRoleName: String = "DeveloperRole"
    var ssmParameterPath: String = "/engineering-artifacts/"
    var enableRoleAssumption: Boolean = true
    var enableSsmFetch: Boolean = true
    var failOnAwsErrors: Boolean = false
}
