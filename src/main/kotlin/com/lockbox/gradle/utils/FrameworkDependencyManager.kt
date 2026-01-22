package com.lockbox.gradle.utils

import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import io.spring.gradle.dependencymanagement.dsl.ImportsHandler
import io.spring.gradle.dependencymanagement.dsl.DependenciesHandler
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import java.io.File

/**
 * Manages dependency versions for Lockbox projects using Spring Dependency Management Plugin.
 *
 * This utility enables seamless dependency management in both composite builds and
 * published artifact scenarios by:
 *
 * **Composite Build Mode** (when `lockbox-framework` is an included build):
 * - Parses `lockbox-framework/gradle/libs.versions.toml` directly
 * - Configures Spring Dependency Management Plugin DSL with extracted versions
 * - No need to publish `framework-platform` to `mavenLocal`
 *
 * **Published Mode** (when consuming from CodeArtifact/Maven):
 * - Imports `com.lockbox:framework-platform` BOM
 * - Versions are managed by the published platform artifact
 *
 * This approach uses Maven-style dependency management (via Spring plugin) instead of
 * Gradle's native `java-platform` which has variant attribute issues in composite builds.
 *
 * @see [configureDependencyManagement] for the main entry point
 */
object FrameworkDependencyManager {

    /** The name of the lockbox-framework directory to search for. */
    private const val FRAMEWORK_DIR_NAME = "lockbox-framework"

    /** The relative path to the version catalog within the framework. */
    private const val VERSION_CATALOG_PATH = "gradle/libs.versions.toml"

    /** Maven coordinates for the framework-platform BOM. */
    private const val PLATFORM_GROUP = "com.lockbox"
    private const val PLATFORM_ARTIFACT = "framework-platform"

    /** Library names that represent BOMs to import (not dependency constraints). */
    private val BOM_LIBRARY_NAMES = setOf(
        "spring-boot-bom",
        "spring-cloud-aws-bom",
        "aws-sdk-bom",
        "aws-xray-bom",
        "opentelemetry-bom"
    )

    /**
     * Configures dependency management for the given project.
     *
     * Automatically detects whether the project is in a composite build with
     * `lockbox-framework` and configures the appropriate strategy.
     *
     * @param project The Gradle project to configure
     * @param logger Optional logger for diagnostic output
     */
    @JvmStatic
    fun configureDependencyManagement(project: Project, logger: Logger? = null) {
        val log = logger ?: project.logger

        // Check if we're in a composite build with lockbox-framework
        val frameworkDir = findFrameworkDir(project)

        if (frameworkDir != null) {
            log.lifecycle("FrameworkDependencyManager: Composite build detected, parsing ${frameworkDir.name}/gradle/libs.versions.toml")
            configureFromVersionCatalog(project, frameworkDir, log)
        } else {
            log.lifecycle("FrameworkDependencyManager: Using published framework-platform BOM")
            configureFromPublishedBom(project, log)
        }
    }

    /**
     * Finds the lockbox-framework directory if this project is part of a composite build.
     *
     * Search strategy:
     * 1. Check if the project itself IS lockbox-framework
     * 2. Check if lockbox-framework is an included build (composite build)
     * 3. Check if lockbox-framework exists as a sibling directory
     *
     * @param project The Gradle project context
     * @return The framework directory if found, null otherwise
     */
    @JvmStatic
    fun findFrameworkDir(project: Project): File? {
        // Check if we ARE the lockbox-framework project
        if (project.rootProject.name == FRAMEWORK_DIR_NAME) {
            return project.rootProject.projectDir
        }

        // Check if lockbox-framework is an included build (composite build)
        // gradle.includedBuilds contains all included builds
        val includedBuild = project.gradle.includedBuilds.find { it.name == FRAMEWORK_DIR_NAME }
        if (includedBuild != null) {
            return includedBuild.projectDir
        }

        // Check sibling directory (for local development without composite build)
        val siblingDir = project.rootProject.projectDir.parentFile?.resolve(FRAMEWORK_DIR_NAME)
        if (siblingDir?.exists() == true && siblingDir.resolve(VERSION_CATALOG_PATH).exists()) {
            return siblingDir
        }

        return null
    }

    /**
     * Configures dependency management by parsing the framework's version catalog.
     *
     * This is used in composite build mode where we have direct access to the
     * framework's source files.
     */
    private fun configureFromVersionCatalog(project: Project, frameworkDir: File, logger: Logger) {
        val tomlFile = frameworkDir.resolve(VERSION_CATALOG_PATH)
        if (!tomlFile.exists()) {
            logger.warn("FrameworkDependencyManager: Version catalog not found at ${tomlFile.absolutePath}")
            // Fall back to published BOM
            configureFromPublishedBom(project, logger)
            return
        }

        // Parse the TOML file
        val catalog = parseVersionCatalog(tomlFile, logger)

        // Get the dependency management extension
        val depMgmt = project.extensions.findByType(DependencyManagementExtension::class.java)
        if (depMgmt == null) {
            logger.warn("FrameworkDependencyManager: DependencyManagementExtension not found. " +
                    "Ensure io.spring.dependency-management plugin is applied.")
            return
        }

        // Configure BOMs first (imports)
        val bomAction = object : Action<ImportsHandler> {
            override fun execute(imports: ImportsHandler) {
                catalog.boms.forEach { (_, coordinates) ->
                    logger.info("FrameworkDependencyManager: Importing BOM $coordinates")
                    imports.mavenBom(coordinates)
                }
            }
        }
        depMgmt.imports(bomAction)

        // Configure direct dependencies
        val dependencyAction = object : Action<DependenciesHandler> {
            override fun execute(deps: DependenciesHandler) {
                catalog.dependencies.forEach { (_, coordinates) ->
                    logger.info("FrameworkDependencyManager: Managing dependency $coordinates")
                    deps.dependency(coordinates)
                }
            }
        }
        depMgmt.dependencies(dependencyAction)

        logger.lifecycle("FrameworkDependencyManager: Configured ${catalog.boms.size} BOMs and ${catalog.dependencies.size} dependencies from version catalog")
    }

    /**
     * Configures dependency management by importing the published framework-platform BOM.
     *
     * This is used when lockbox-framework is not available as a composite build.
     */
    private fun configureFromPublishedBom(project: Project, logger: Logger) {
        val depMgmt = project.extensions.findByType(DependencyManagementExtension::class.java)
        if (depMgmt == null) {
            logger.warn("FrameworkDependencyManager: DependencyManagementExtension not found. " +
                    "Ensure io.spring.dependency-management plugin is applied.")
            return
        }

        // Resolve the version from the project's version catalog or properties
        val version = FrameworkPlatformResolver.resolve(project)
        val bomCoordinates = "$PLATFORM_GROUP:$PLATFORM_ARTIFACT:$version"

        logger.lifecycle("FrameworkDependencyManager: Importing published BOM $bomCoordinates")

        val publishedBomAction = object : Action<ImportsHandler> {
            override fun execute(imports: ImportsHandler) {
                imports.mavenBom(bomCoordinates)
            }
        }
        depMgmt.imports(publishedBomAction)
    }

    /**
     * Parses a Gradle version catalog TOML file and extracts BOMs and dependencies.
     *
     * Note: We use regex-based parsing instead of toml4j because the Gradle
     * version catalog format uses inline tables with dotted keys (version.ref)
     * which toml4j doesn't support.
     *
     * @param tomlFile The libs.versions.toml file to parse
     * @param logger Logger for diagnostic output
     * @return ParsedCatalog containing extracted BOMs and dependencies
     */
    @JvmStatic
    fun parseVersionCatalog(tomlFile: File, logger: Logger): ParsedCatalog {
        val content = tomlFile.readText()
        
        // Parse versions section
        val versions = parseVersionsSection(content, logger)
        logger.info("FrameworkDependencyManager: Parsed ${versions.size} versions")
        
        // Parse libraries section
        val boms = mutableMapOf<String, String>()
        val dependencies = mutableMapOf<String, String>()
        
        parseLibrariesSection(content, versions, logger).forEach { (name, coordinates) ->
            if (BOM_LIBRARY_NAMES.contains(name)) {
                boms[name] = coordinates
            } else {
                dependencies[name] = coordinates
            }
        }

        return ParsedCatalog(boms, dependencies)
    }

    /**
     * Parses the [versions] section of a TOML file.
     */
    private fun parseVersionsSection(content: String, logger: Logger): Map<String, String> {
        val versions = mutableMapOf<String, String>()
        
        // Find the [versions] section
        val versionsStart = content.indexOf("[versions]")
        if (versionsStart == -1) return versions
        
        // Find the next section (starts with [)
        val nextSection = content.indexOf("\n[", versionsStart + 1)
        val versionsSection = if (nextSection != -1) {
            content.substring(versionsStart, nextSection)
        } else {
            content.substring(versionsStart)
        }
        
        // Parse version = "value" lines
        val versionPattern = Regex("""^(\S+)\s*=\s*"([^"]+)"(?:\s*#.*)?$""", RegexOption.MULTILINE)
        versionPattern.findAll(versionsSection).forEach { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2]
            versions[key] = value
            logger.info("FrameworkDependencyManager: Found version $key = $value")
        }
        
        return versions
    }

    /**
     * Parses the [libraries] section of a TOML file.
     */
    private fun parseLibrariesSection(
        content: String,
        versions: Map<String, String>,
        logger: Logger
    ): Map<String, String> {
        val libraries = mutableMapOf<String, String>()
        
        // Find the [libraries] section
        val librariesStart = content.indexOf("[libraries]")
        if (librariesStart == -1) return libraries
        
        // Find the next section (starts with [)
        val nextSection = content.indexOf("\n[", librariesStart + 1)
        val librariesSection = if (nextSection != -1) {
            content.substring(librariesStart, nextSection)
        } else {
            content.substring(librariesStart)
        }
        
        // Pattern for library definitions with version.ref
        // e.g., spring-boot-bom = { group = "org.springframework.boot", name = "spring-boot-dependencies", version.ref = "spring-boot" }
        val versionRefPattern = Regex(
            """^(\S+)\s*=\s*\{\s*group\s*=\s*"([^"]+)",\s*name\s*=\s*"([^"]+)",\s*version\.ref\s*=\s*"([^"]+)"\s*\}""",
            RegexOption.MULTILINE
        )
        
        // Pattern for library definitions with direct version
        // e.g., mapstruct-lombok = { group = "org.projectlombok", name = "lombok-mapstruct-binding", version = "0.2.0" }
        val directVersionPattern = Regex(
            """^(\S+)\s*=\s*\{\s*group\s*=\s*"([^"]+)",\s*name\s*=\s*"([^"]+)",\s*version\s*=\s*"([^"]+)"\s*\}""",
            RegexOption.MULTILINE
        )
        
        // Pattern for library definitions without version (managed by BOM)
        // e.g., junit-jupiter = { group = "org.junit.jupiter", name = "junit-jupiter" }
        val noVersionPattern = Regex(
            """^(\S+)\s*=\s*\{\s*group\s*=\s*"([^"]+)",\s*name\s*=\s*"([^"]+)"\s*\}""",
            RegexOption.MULTILINE
        )
        
        // Process version.ref libraries
        versionRefPattern.findAll(librariesSection).forEach { match ->
            val name = match.groupValues[1]
            val group = match.groupValues[2]
            val artifact = match.groupValues[3]
            val versionRef = match.groupValues[4]
            val version = versions[versionRef]
            
            if (version != null) {
                libraries[name] = "$group:$artifact:$version"
                logger.info("FrameworkDependencyManager: Found library $name = $group:$artifact:$version (via $versionRef)")
            } else {
                logger.warn("FrameworkDependencyManager: Version ref '$versionRef' not found for library $name")
            }
        }
        
        // Process direct version libraries
        directVersionPattern.findAll(librariesSection).forEach { match ->
            val name = match.groupValues[1]
            val group = match.groupValues[2]
            val artifact = match.groupValues[3]
            val version = match.groupValues[4]
            
            libraries[name] = "$group:$artifact:$version"
            logger.info("FrameworkDependencyManager: Found library $name = $group:$artifact:$version (direct)")
        }
        
        // Log libraries without version (we skip them, they're managed by BOM)
        noVersionPattern.findAll(librariesSection).forEach { match ->
            val name = match.groupValues[1]
            logger.info("FrameworkDependencyManager: Skipping $name (no version, managed by BOM)")
        }
        
        return libraries
    }

    /**
     * Data class representing a parsed version catalog.
     *
     * @property boms Map of BOM names to their Maven coordinates (group:artifact:version)
     * @property dependencies Map of dependency names to their Maven coordinates
     */
    data class ParsedCatalog(
        val boms: Map<String, String>,
        val dependencies: Map<String, String>
    )
}
