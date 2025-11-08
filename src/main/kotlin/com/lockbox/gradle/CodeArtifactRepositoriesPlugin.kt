package com.lockbox.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository

/**
 * Gradle plugin for configuring CodeArtifact Maven repositories.
 *
 * Configures Maven repositories for AWS CodeArtifact with automatic fallback to Maven Central
 * when CodeArtifact credentials are unavailable.
 *
 * Expects the following properties to be set (typically by AwsEnvironmentPlugin):
 * - codeartifact.authToken: CodeArtifact authorization token
 * - codeartifact.domain: CodeArtifact domain name
 * - codeartifact.accountId: AWS account ID
 * - aws.region: AWS region
 * - codeartifact.javaRepository: Release repository name
 * - codeartifact.javaSnapshotsRepository: Snapshots repository name
 *
 * Configuration can be customized via extension:
 * ```kotlin
 * codeartifactRepositories {
 *     enableFallback = true
 *     releasesRepositoryName = "my-releases"
 *     snapshotsRepositoryName = "my-snapshots"
 * }
 * ```
 */
class CodeArtifactRepositoriesPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Create and register the extension
        val extension = project.extensions.create(
            "codeartifactRepositories",
            CodeArtifactRepositoriesExtension::class.java
        )

        // Configure repositories after project evaluation
        project.afterEvaluate {
            configureRepositories(project, extension)
        }
    }

    private fun configureRepositories(project: Project, extension: CodeArtifactRepositoriesExtension) {
        val logger = project.logger

        // Check if CodeArtifact token is available
        val authToken = project.findProperty("codeartifact.authToken")?.toString()

        if (authToken != null && authToken.isNotEmpty()) {
            logger.lifecycle("Configuring CodeArtifact repositories")

            try {
                val domain = project.findProperty("codeartifact.domain")?.toString()
                if (domain.isNullOrBlank()) {
                    logger.warn("CodeArtifact domain not set. Cannot configure repositories.")
                    if (extension.enableFallback) {
                        logger.lifecycle("Falling back to Maven Central")
                        project.repositories.mavenCentral()
                    }
                    return
                }

                val accountId = project.findProperty("codeartifact.accountId")?.toString()
                if (accountId.isNullOrBlank()) {
                    logger.warn("CodeArtifact account ID not set. Cannot configure repositories.")
                    if (extension.enableFallback) {
                        logger.lifecycle("Falling back to Maven Central")
                        project.repositories.mavenCentral()
                    }
                    return
                }

                val region = project.findProperty("aws.region")?.toString() ?: "us-east-1"
                val releasesRepo = project.findProperty("codeartifact.javaRepository")?.toString()
                    ?: extension.releasesRepositoryName
                val snapshotsRepo = project.findProperty("codeartifact.javaSnapshotsRepository")?.toString()
                    ?: extension.snapshotsRepositoryName

                // Configure releases repository
                project.repositories.maven {
                    name = "CodeArtifactReleases"
                    url = project.uri(
                        "https://${domain}-${accountId}.d.codeartifact.${region}.amazonaws.com/maven/${releasesRepo}/"
                    )
                    credentials {
                        username = "aws"
                        password = authToken
                    }
                }

                // Configure snapshots repository
                project.repositories.maven {
                    name = "CodeArtifactSnapshots"
                    url = project.uri(
                        "https://${domain}-${accountId}.d.codeartifact.${region}.amazonaws.com/maven/${snapshotsRepo}/"
                    )
                    credentials {
                        username = "aws"
                        password = authToken
                    }
                }

                logger.lifecycle("CodeArtifact repositories configured successfully")
            } catch (e: Exception) {
                logger.warn("Failed to configure CodeArtifact repositories: ${e.message}")
                if (extension.enableFallback) {
                    logger.lifecycle("Falling back to Maven Central")
                    project.repositories.mavenCentral()
                }
            }
        } else {
            logger.debug("CodeArtifact token not available")
            if (extension.enableFallback) {
                logger.lifecycle("Configuring Maven Central as fallback repository")
                project.repositories.mavenCentral()
            } else {
                logger.warn("CodeArtifact token unavailable and fallback disabled")
            }
        }
    }
}

/**
 * Extension for configuring the CodeArtifact repositories plugin.
 */
open class CodeArtifactRepositoriesExtension {
    var enableFallback: Boolean = true
    var releasesRepositoryName: String = "releases"
    var snapshotsRepositoryName: String = "snapshots"
}
