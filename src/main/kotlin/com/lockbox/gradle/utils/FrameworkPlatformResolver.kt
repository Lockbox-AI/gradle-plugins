package com.lockbox.gradle.utils

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension

/**
 * Utility for resolving the framework-platform version in convention plugins.
 *
 * This resolver provides a consistent way to determine the framework-platform version
 * for external consumers (projects not part of the lockbox-framework multi-project build).
 *
 * Resolution priority:
 * 1. **Version catalog**: `libs.versions.toml` with `lockbox-framework` version key
 * 2. **Environment variable**: `FRAMEWORK_PLATFORM_VERSION` (for CI coordination)
 * 3. **Gradle property**: `frameworkPlatformVersion` in `gradle.properties`
 *
 * If none of these are configured, a descriptive [GradleException] is thrown with
 * instructions for resolving the issue.
 *
 * @see [resolve] for the main entry point
 */
object FrameworkPlatformResolver {

    /** The version catalog name to search for (standard Gradle convention). */
    private const val VERSION_CATALOG_NAME = "libs"

    /** The version key within the catalog for lockbox-framework. */
    private const val VERSION_KEY = "lockbox-framework"

    /** Environment variable name for CI/CD override. */
    private const val ENV_VAR_NAME = "FRAMEWORK_PLATFORM_VERSION"

    /** Gradle property name for local configuration. */
    private const val GRADLE_PROPERTY_NAME = "frameworkPlatformVersion"

    /** Maven coordinates for the framework-platform BOM. */
    private const val PLATFORM_GROUP = "com.lockbox"
    private const val PLATFORM_ARTIFACT = "framework-platform"

    /**
     * Resolves the framework-platform version using the priority chain.
     *
     * @param project The Gradle project context for accessing extensions and properties
     * @return The resolved version string
     * @throws GradleException if no version can be resolved from any source
     */
    @JvmStatic
    fun resolve(project: Project): String {
        // Priority 1: Version catalog
        val catalogVersion = resolveFromVersionCatalog(project)
        if (catalogVersion != null) {
            return catalogVersion
        }

        // Priority 2: Environment variable
        val envVersion = System.getenv(ENV_VAR_NAME)
        if (!envVersion.isNullOrBlank()) {
            return envVersion
        }

        // Priority 3: Gradle property
        val propertyVersion = project.findProperty(GRADLE_PROPERTY_NAME)?.toString()
        if (!propertyVersion.isNullOrBlank()) {
            return propertyVersion
        }

        // No version found - throw descriptive error
        throw GradleException(
            buildString {
                appendLine("Framework platform version not found. Configure one of:")
                appendLine("  1. Version catalog: Add '$VERSION_KEY = \"x.y.z\"' to gradle/$VERSION_CATALOG_NAME.versions.toml")
                appendLine("  2. Environment variable: Set $ENV_VAR_NAME")
                appendLine("  3. Gradle property: Add '$GRADLE_PROPERTY_NAME=x.y.z' to gradle.properties")
            }
        )
    }

    /**
     * Returns the Maven coordinate string for the framework-platform with the resolved version.
     *
     * @param project The Gradle project context
     * @return Maven coordinate in format "group:artifact:version"
     */
    @JvmStatic
    fun getMavenCoordinate(project: Project): String {
        val version = resolve(project)
        return "$PLATFORM_GROUP:$PLATFORM_ARTIFACT:$version"
    }

    /**
     * Attempts to resolve the version from the project's version catalog.
     *
     * @param project The Gradle project context
     * @return The version string if found, null otherwise
     */
    private fun resolveFromVersionCatalog(project: Project): String? {
        return project.extensions
            .findByType(VersionCatalogsExtension::class.java)
            ?.find(VERSION_CATALOG_NAME)?.orElse(null)
            ?.findVersion(VERSION_KEY)?.orElse(null)
            ?.requiredVersion
    }
}
