package com.lockbox.gradle

import org.gradle.api.initialization.Settings
import org.gradle.api.Plugin

/**
 * Gradle settings plugin for configuring CodeArtifact plugin repositories.
 *
 * Configures the pluginManagement repositories for AWS CodeArtifact with automatic fallback
 * to Gradle Plugin Portal when CodeArtifact credentials are unavailable.
 *
 * This plugin should be applied in settings.gradle.kts to configure plugin resolution:
 * ```kotlin
 * pluginManagement {
 *     apply(plugin = "com.lockbox.codeartifact-repositories-settings")
 * }
 * ```
 *
 * Expects the following properties to be set (typically via gradle.properties):
 * - aws.codeartifact.domain: CodeArtifact domain name
 * - aws.codeartifact.accountId: AWS account ID
 * - aws.codeartifact.region: AWS region
 * - codeartifact.authToken: CodeArtifact authorization token (from environment or AwsEnvironmentPlugin)
 */
class CodeArtifactRepositoriesSettingsPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        println("Configuring CodeArtifact plugin repositories")

        // Read configuration from gradle.properties and environment
        val domain = settings.gradle.startParameter.projectProperties["aws.codeartifact.domain"]
            ?: System.getenv("CODEARTIFACT_DOMAIN")

        val accountId = settings.gradle.startParameter.projectProperties["aws.codeartifact.accountId"]
            ?: System.getenv("CODEARTIFACT_ACCOUNT_ID")

        val region = settings.gradle.startParameter.projectProperties["aws.codeartifact.region"]
            ?: System.getenv("AWS_REGION")
            ?: "us-east-1"

        val repositoryName = settings.gradle.startParameter.projectProperties["codeartifact.plugin.repository"]
            ?: System.getenv("CODEARTIFACT_PLUGIN_REPOSITORY")
            ?: "releases"

        val authToken = System.getenv("CODEARTIFACT_AUTH_TOKEN") ?: ""

        // Configure plugin repositories
        settings.pluginManagement {
            repositories {
                // Add CodeArtifact repository if token and configuration are available
                if (authToken.isNotEmpty() && !domain.isNullOrBlank() && !accountId.isNullOrBlank()) {
                    println("Adding CodeArtifact plugin repository")
                    maven {
                        name = "CodeArtifactPlugins"
                        url = java.net.URI.create(
                            "https://${domain}-${accountId}.d.codeartifact.${region}.amazonaws.com/maven/${repositoryName}/"
                        )
                        credentials {
                            username = "aws"
                            password = authToken
                        }
                    }
                } else {
                    if (authToken.isEmpty()) {
                        println("CodeArtifact token not available, skipping CodeArtifact plugin repository")
                    } else {
                        println("CodeArtifact configuration incomplete (domain or account ID missing), skipping CodeArtifact plugin repository")
                    }
                }

                // Always add Gradle Plugin Portal as fallback
                gradlePluginPortal()
                mavenCentral()
            }
        }

        println("Plugin repositories configured")
    }
}
