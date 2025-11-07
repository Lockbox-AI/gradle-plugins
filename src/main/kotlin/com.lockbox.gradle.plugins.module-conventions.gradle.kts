import com.lockbox.gradle.tasks.GenerateSiteTask
import com.lockbox.gradle.tasks.WriteLatestRedirectTask
import com.lockbox.gradle.tasks.UploadDocsTask
import org.gradle.api.tasks.Sync

/**
 * Lockbox Module Conventions Plugin
 * 
 * This convention plugin defines shared configuration for all Lockbox non-framework modules.
 * It builds on top of lockbox.gradle.plugins.java-conventions and adds:
 * - Lombok plugin application
 * - Standard module dependencies (javax-annotations, commons-lang3, test frameworks)
 * - Maven publishing conventions with automatic artifactId and POM naming
 * - Site generation and documentation publishing
 * 
 * All modules applying this plugin will have consistent build behavior and publishing configuration.
 * Dependency versions are managed by the framework-platform BOM.
 */

plugins {
    // Apply the base Java conventions plugin first
    id("com.lockbox.gradle.plugins.java-conventions")
    
    // Apply Lombok plugin for all modules
    // This is applied here (not in java-conventions) to avoid Gradle 9.2.0 Tooling API issues
    id("io.freefair.lombok")
}

// ========================================
// Standard Module Dependencies
// ========================================

dependencies {
    // Import the framework platform for version management
    // Use project reference when available (in multi-project builds)
    if (rootProject.findProject(":framework-platform") != null) {
        implementation(platform(project(":framework-platform")))
        testImplementation(platform(project(":framework-platform")))
        add("integrationTestImplementation", platform(project(":framework-platform")))
    } else {
        // Fall back to Maven coordinate for published plugins
        // Use ARTIFACT_VERSION if set (coordinated by build script), 
        // otherwise read from gradle.properties, 
        // finally fall back to rootProject.version
        val platformVersion = System.getenv("ARTIFACT_VERSION")
            ?: findProperty("frameworkPlatformVersion")?.toString() 
            ?: rootProject.version.toString()
        implementation(platform("com.lockbox:framework-platform:${platformVersion}"))
        testImplementation(platform("com.lockbox:framework-platform:${platformVersion}"))
        add("integrationTestImplementation", platform("com.lockbox:framework-platform:${platformVersion}"))
    }
    
    // JavaX Annotations (required for Lombok @Generated annotation)
    // Version is managed by framework-platform BOM
    compileOnly("javax.annotation:javax.annotation-api")
    
    // Lombok (required for annotation processing)
    // Version is managed by framework-platform BOM
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    
    // Test dependencies - versions managed by framework-platform BOM
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
        exclude(group = "org.yaml", module = "snakeyaml")
    }
    testImplementation("org.yaml:snakeyaml")
    
    // Integration test dependencies - versions managed by framework-platform BOM
    add("integrationTestImplementation", "org.springframework.boot:spring-boot-starter-test") {
        exclude(mapOf("group" to "org.junit.vintage", "module" to "junit-vintage-engine"))
        exclude(mapOf("group" to "org.yaml", "module" to "snakeyaml"))
    }
    add("integrationTestImplementation", "org.yaml:snakeyaml")
    add("integrationTestImplementation", "org.testcontainers:testcontainers")
    add("integrationTestImplementation", "org.testcontainers:junit-jupiter")
    add("integrationTestImplementation", "com.h2database:h2")
    add("integrationTestImplementation", "org.awaitility:awaitility")
    
    // Note: Modules should declare their own additional implementation dependencies
    // (e.g., commons-lang3) as needed. This provides standard test dependencies.
}

// ========================================
// Test Fixtures Standard Dependencies
// ========================================

// Apply test fixtures dependencies when the java-test-fixtures plugin is present
plugins.withId("java-test-fixtures") {
    dependencies {
        // Platform BOM for version management
        add("testFixturesImplementation", platform(project(":framework-platform")))
        
        // Lombok support for testFixtures
        add("testFixturesCompileOnly", "javax.annotation:javax.annotation-api")
        add("testFixturesCompileOnly", "org.projectlombok:lombok")
        add("testFixturesAnnotationProcessor", "org.projectlombok:lombok")
        
        // Spring JDBC for schema initializers (used by DatabaseHelper classes)
        add("testFixturesImplementation", "org.springframework:spring-jdbc")
        add("testFixturesImplementation", "org.springframework:spring-core")
        
        // Core lockbox dependency (always needed by test fixtures)
        add("testFixturesImplementation", project(":lockbox-core"))
        
        // JavaFaker for test data generation
        // Version managed by framework-platform BOM
        val javafakerDep = project.dependencies.create("com.github.javafaker:javafaker")
        (javafakerDep as ModuleDependency).exclude(mapOf("group" to "org.yaml", "module" to "snakeyaml"))
        add("testFixturesImplementation", javafakerDep)
    }
}

// ========================================
// Common Test Fixtures Usage
// ========================================

// All modules commonly use lockbox-core and lockbox-data test fixtures
dependencies {
    /*
     * Note: Three foundational modules exist in the framework:
     * - lockbox-core: Core domain models, utilities, and abstractions
     * - lockbox-data: Data access layer with JDBC templates and datasource support
     * - lockbox-cache: Caching abstraction and implementations
     * 
     * These modules are used by all other modules in the framework.
     * 
     * These foundational modules are need to be excluded from the addition of
     * test fixture addition to avoid cyclic dependencies.
     *
     * All other modules require the test fixtures of the foundational modules.
     */
    val foundationalModules = setOf("lockbox-cache", "lockbox-core", "lockbox-data")
    if (!foundationalModules.contains(project.name)) {
        // Test source set dependencies
        testImplementation(testFixtures(project(":lockbox-core")))
        testImplementation(testFixtures(project(":lockbox-data")))

        // Integration test source set dependencies
        add("integrationTestImplementation", testFixtures(project(":lockbox-core")))
        add("integrationTestImplementation", testFixtures(project(":lockbox-data")))
    }
    
    // Integration tests commonly need javax.annotation for Lombok @Generated and @NonNull annotations
    add("integrationTestCompileOnly", "javax.annotation:javax.annotation-api")
}

// ========================================
// Test Fixtures - Module-Specific Dependencies
// ========================================
// Modules with test fixtures should add their own module-specific dependencies:
// 1. Cross-module testFixtures dependencies as needed:
//    testFixturesImplementation(testFixtures(project(":lockbox-data-[other-module]")))
// 2. Self-reference for integration tests:
//    add("integrationTestImplementation", testFixtures(project(":lockbox-data-[module]")))
// 3. Module-specific implementations (e.g., lockbox-cache for services using CacheRegionManager)

// ========================================
// Site Generation
// ========================================

// Register the generateSite task using the proper task class
tasks.register<GenerateSiteTask>("generateSite") {
    group = "documentation"
    description = "Generates a comprehensive project site with aggregated reports and metrics"
    
    // Set task inputs
    projectName.set(project.name)
    projectVersion.set(project.version.toString())
    gradleVersion.set(gradle.gradleVersion)
    buildDir.set(project.layout.buildDirectory)
    
    // For multi-module projects: set module name and use root project's site directory
    moduleName.set(project.name)
    siteDir.set(rootProject.layout.buildDirectory.dir("site"))
    
    // Explicitly declare the actual output subdirectory to help Gradle with dependency tracking
    // The task writes to rootProject.build/site/{module-name}/, not the entire site directory
    outputs.dir(rootProject.layout.buildDirectory.dir("site/${project.name}"))
        .withPropertyName("moduleOutputDirectory")
    
    // Depend on all report generation tasks
    dependsOn(
        "test",
        "jacocoTestReport",
        "checkstyleMain",
        "checkstyleTest",
        "pmdMain",
        "pmdTest",
        "spotbugsMain",
        "spotbugsTest",
        "javadoc",
        "projectReport"
    )
    
    // Depend on publishing artifact tasks to ensure outputs are available
    dependsOn(
        "javadocJar",
        "sourcesJar",
        "jar",
        "generateMetadataFileForMavenJavaPublication",
        "writeLatestRedirect",
        "generatePomFileForMavenJavaPublication"
    )
}

// ========================================
// Documentation Portal Publishing
// ========================================

val docsBucketValue = (findProperty("docs.bucket") as String?)
    ?: System.getenv("DOCS_BUCKET")
    ?: (rootProject.extra.properties["DOCS_PORTAL_BUCKET"] as? String)

val stageDir = layout.buildDirectory.dir("docsUpload")

val writeLatestRedirect by tasks.registering(WriteLatestRedirectTask::class) {
    projectVersion.set(version.toString())
    artifactType.set("site")
    projectSlug.set("lockbox-framework")
    s3BasePrefix.set("site/lockbox-framework")
    outputDir.set(stageDir.map { it.dir("site/lockbox-framework/latest") })
}

val stageDocs by tasks.registering(Sync::class) {
    val generateSiteTask = tasks.named("generateSite")
    dependsOn(generateSiteTask)
    
    // For multi-module projects: site is generated in rootProject.build/site/{module-name}/
    // Depend on root's generateSite which coordinates all modules to avoid implicit dependencies
    val moduleSiteDir = rootProject.layout.buildDirectory.dir("site/${project.name}")
    
    // The root project's generateSite task depends on all modules' generateSite tasks,
    // so by depending on it, we ensure all site generation is complete before staging
    rootProject.tasks.findByName("generateSite")?.let { rootGenerateSite ->
        dependsOn(rootGenerateSite)
    }
    
    from(moduleSiteDir)
    into(stageDir.map { it.dir("site/lockbox-framework/$version") })
    
    doLast {
        logger.lifecycle("Staged documentation to: ${destinationDir.absolutePath}")
    }
}

val assembleDocsUpload by tasks.registering {
    dependsOn(stageDocs, writeLatestRedirect)
    group = "documentation"
    description = "Assembles all documentation artifacts for upload"
}

val uploadDocs by tasks.registering(UploadDocsTask::class) {
    dependsOn(assembleDocsUpload)
    group = "documentation"
    description = "Uploads documentation to S3 docs portal"
    
    if (docsBucketValue != null) {
        docsBucket.set(docsBucketValue)
    }
    s3BasePrefix.set("site/lockbox-framework")
    projectVersion.set(version.toString())
    artifactType.set("site")
    projectSlug.set("lockbox-framework")
    stagingDir.set(stageDir)
}

tasks.register("publishDocs") {
    dependsOn(uploadDocs)
    group = "documentation"
    description = "Builds comprehensive site and publishes it to engineering docs portal"
    
    val projectVersion = version.toString()
    
    doLast {
        val versionType = if (projectVersion.endsWith("-SNAPSHOT")) "Snapshot" else "Release"
        
        logger.lifecycle("")
        logger.lifecycle("=" .repeat(70))
        logger.lifecycle("Documentation published successfully!")
        logger.lifecycle("=" .repeat(70))
        logger.lifecycle("Project:      lockbox-framework")
        logger.lifecycle("Type:         site")
        logger.lifecycle("Version:      $projectVersion ($versionType)")
        logger.lifecycle("Latest URL:   https://engineering-docs.lockboxai.com/site/lockbox-framework/latest/")
        logger.lifecycle("Version URL:  https://engineering-docs.lockboxai.com/site/lockbox-framework/$projectVersion/")
        logger.lifecycle("=" .repeat(70))
        logger.lifecycle("")
        logger.lifecycle("Portal displays:")
        if (projectVersion.endsWith("-SNAPSHOT")) {
            logger.lifecycle("  - This version appears under 'Latest Snapshot' (yellow badge)")
        } else {
            logger.lifecycle("  - This version appears under 'Latest Release' (green badge)")
        }
        logger.lifecycle("")
    }
}

tasks.register<Exec>("previewDocs") {
    dependsOn("generateSite")
    group = "documentation"
    description = "Builds site and opens it in browser"
    
    val indexFile = layout.buildDirectory.dir("site").map { it.file("index.html").asFile }
    
    doFirst {
        val file = indexFile.get()
        if (!file.exists()) {
            throw GradleException("Site index.html not found at ${file.absolutePath}. Run 'generateSite' first.")
        }
        
        val cmd = when {
            org.gradle.internal.os.OperatingSystem.current().isMacOsX ->
                listOf("open", file.absolutePath)
            org.gradle.internal.os.OperatingSystem.current().isLinux ->
                listOf("xdg-open", file.absolutePath)
            org.gradle.internal.os.OperatingSystem.current().isWindows ->
                listOf("cmd", "/c", "start", file.absolutePath)
            else ->
                throw GradleException("Unsupported operating system")
        }
        
        commandLine(cmd)
        logger.lifecycle("Opening documentation in browser: ${file.absolutePath}")
    }
}

tasks.register<Delete>("cleanDocs") {
    group = "documentation"
    description = "Cleans documentation staging directory"
    delete(stageDir)
}
