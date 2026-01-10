import com.lockbox.gradle.tasks.GenerateSiteTask

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
    id("io.github.lockboxai.java-conventions")
    
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
    // Testcontainers 2.x uses testcontainers- prefix for module artifacts
    add("integrationTestImplementation", "org.testcontainers:testcontainers")
    add("integrationTestImplementation", "org.testcontainers:testcontainers-junit-jupiter")
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
        "generatePomFileForMavenJavaPublication"
    )
}

// ========================================
// Local Documentation Preview
// ========================================
// Note: For multi-module projects, staging and publishing are handled at the root level.
// See: gradle/docs-portal-publishing.gradle.kts in the root project.

/**
 * Preview the generated module documentation in a browser.
 * 
 * For multi-module projects, this opens the module's specific documentation page.
 * To view the aggregated index, run this task from the root project.
 */
tasks.register<Exec>("previewDocs") {
    dependsOn("generateSite")
    group = "documentation"
    description = "Builds module site and opens it in browser"
    
    // For modules, the site is at rootProject.build/site/{module-name}/index.html
    val indexFile = rootProject.layout.buildDirectory.dir("site/${project.name}").map { it.file("index.html").asFile }
    
    doFirst {
        val file = indexFile.get()
        if (!file.exists()) {
            throw GradleException("Module site index.html not found at ${file.absolutePath}. Run 'generateSite' first.")
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
        logger.lifecycle("Opening module documentation in browser: ${file.absolutePath}")
    }
}
