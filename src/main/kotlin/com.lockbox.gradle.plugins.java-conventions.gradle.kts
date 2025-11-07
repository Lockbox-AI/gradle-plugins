/**
 * Lockbox Java Conventions Plugin
 * 
 * This convention plugin defines shared configuration for all Lockbox Java modules.
 * It centralizes:
 * - Java toolchain configuration
 * - Common plugins (testing, quality, documentation)
 * - Quality tool configurations (Checkstyle, PMD, SpotBugs)
 * - Test framework setup
 * - Reproducible build settings with dependency locking
 * 
 * All version references use centralized constants defined below.
 */

plugins {
    java
    `maven-publish`
    jacoco
    id("com.diffplug.spotless")
    checkstyle
    pmd
    id("com.github.spotbugs")
    id("project-report")
    `java-test-fixtures`  // Enable test fixtures support
}

// ========================================
// Version Constants
// ========================================
// These must match versions in framework-platform.gradle.kts
object Versions {
    const val JAVA = 21
    const val JACOCO = "0.8.11"
    const val CHECKSTYLE = "10.20.2"
    const val PMD = "7.9.0"
    const val SPOTBUGS = "4.8.6"
    const val PALANTIR_JAVA_FORMAT = "2.81.0"
    const val LOMBOK = "1.18.36"
}

// ========================================
// Dependency Locking for Reproducible Builds
// ========================================
dependencyLocking {
    lockAllConfigurations()
}

// ========================================
// Dependency Resolution Strategy
// ========================================
// Fix for snakeyaml Android variant resolution issue
// This applies to ALL configurations including testFixtures
configurations.all {
    resolutionStrategy {
        force("org.yaml:snakeyaml:2.4")
        
        // Explicitly exclude Android variants
        eachDependency {
            if (requested.group == "org.yaml" && requested.name == "snakeyaml") {
                useVersion("2.4")
                because("Force standard JAR, not Android variant")
            }
        }
    }
}

// ========================================
// Java Configuration
// ========================================

java {
    withJavadocJar()
    withSourcesJar()
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(Versions.JAVA))
    }
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// ========================================
// Reproducible Builds Configuration
// ========================================

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

// ========================================
// Compilation Configuration
// ========================================

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
    options.release.set(21)
}

// ========================================
// Test Configuration
// ========================================

tasks.withType<Test> {
    useJUnitPlatform()
    
    jvmArgs(
        "-XX:+HeapDumpOnOutOfMemoryError",
        "-XX:+CrashOnOutOfMemoryError"
    )
    
    // Enable parallel test execution for faster test runs
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1
    
    // JUnit Jupiter parallel execution configuration
    systemProperty("junit.jupiter.execution.parallel.enabled", "true")
    systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")
    systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "concurrent")
    systemProperty("junit.jupiter.execution.parallel.config.strategy", "dynamic")
    systemProperty("junit.jupiter.execution.parallel.config.dynamic.factor", "1")
    
    systemProperty("aws.region", "us-east-1")
    systemProperty("AWS_REGION", "us-east-1")
    systemProperty("AWS_ACCESS_KEY_ID", "test-key")
    systemProperty("AWS_SECRET_ACCESS_KEY", "test-secret")
}

// ========================================
// Test Suites Configuration (Modern Gradle Approach)
// ========================================

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
            
            dependencies {
                // Note: Test suite dependencies are added in individual modules
                // because they need access to the platform BOM which is module-specific
            }
        }
        
        // Integration test suite
        val integrationTest by registering(JvmTestSuite::class) {
            useJUnitJupiter()
            
            dependencies {
                // Integration tests have access to main sources
                implementation(project())
                
                // Note: Integration test dependencies are added in individual modules
                // because they need access to the platform BOM which is module-specific
            }
            
            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(test)
                        
                        // Integration tests with Testcontainers: disable parallel forks
                        // Each test class gets its own JVM to avoid Testcontainers conflicts
                        maxParallelForks = 1
                        
                        // Separate reports
                        reports {
                            html.required.set(true)
                            junitXml.required.set(true)
                        }
                        
                        // Integration test system properties
                        systemProperty("spring.profiles.active", "integration-test")
                        
                        // Disable JUnit parallel execution for integration tests
                        // Tests share Testcontainers and database state
                        systemProperty("junit.jupiter.execution.parallel.enabled", "false")
                    }
                }
            }
        }
    }
}

// Make check task run integration tests
tasks.named("check") {
    dependsOn(testing.suites.named("integrationTest"))
}

// Optional: Run all tests together
tasks.register("testAll") {
    dependsOn("test", "integrationTest")
    group = "verification"
    description = "Runs all unit and integration tests"
}

// ========================================
// Test Fixtures Configuration
// ========================================
// Note: Test fixtures dependencies should be added by individual modules
// that provide test fixtures, not globally here. Each module can decide
// what dependencies its test fixtures need.

// ========================================
// JaCoCo Configuration
// ========================================

jacoco {
    toolVersion = Versions.JACOCO
}

tasks.named("test") {
    finalizedBy("jacocoTestReport")
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn("test")
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.register<JacocoCoverageVerification>("jacocoCheck") {
    dependsOn("jacocoTestReport")
    
    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.01".toBigDecimal()
            }
        }
    }
}

// ========================================
// Javadoc Configuration
// ========================================

fun javaApiUrl(): String {
    val v = Versions.JAVA
    return if (v <= 10)
        "https://docs.oracle.com/javase/$v/docs/api/"
    else
        "https://docs.oracle.com/en/java/javase/$v/docs/api/"
}

tasks.withType<Javadoc>().configureEach {
    isFailOnError = false
    
    // Skip javadoc generation if there are no public or protected classes
    // This prevents the "No public or protected classes found to document" error
    onlyIf {
        val mainSourceSet = sourceSets.getByName("main")
        val javaSourceFiles = mainSourceSet.allJava.asFileTree.filter { file ->
            file.isFile && file.name.endsWith(".java") && !file.name.contains("package-info")
        }
        if (javaSourceFiles.isEmpty) {
            // No Java files at all, skip javadoc
            return@onlyIf false
        }
        // Check if any file contains public or protected classes/interfaces/enums/annotations
        val hasPublicClasses = javaSourceFiles.any { file ->
            try {
                file.readText().contains(Regex("(public|protected)\\s+(class|interface|enum|@interface)"))
            } catch (e: Exception) {
                false
            }
        }
        hasPublicClasses
    }
    
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        charSet = "UTF-8"
        addBooleanOption("html5", true)
        addStringOption("Xdoclint:none", "-quiet")
        memberLevel = JavadocMemberLevel.PRIVATE
        
        links(javaApiUrl())
        links("https://javadoc.io/doc/org.projectlombok/lombok/${Versions.LOMBOK}/")
        
        addBooleanOption("notimestamp", true)
    }
}

// ========================================
// Spotless Configuration
// ========================================

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    java {
        target("src/main/**/*.java", "src/test/**/*.java")
        targetExclude("build/**")
        
        // Load license header from plugin resources
        val licenseHeaderContent = javaClass.getResourceAsStream("/spotless/license-header.txt")?.bufferedReader()?.readText()
            ?: throw IllegalStateException("License header resource not found in plugin")
        licenseHeader(licenseHeaderContent)
        
        palantirJavaFormat(Versions.PALANTIR_JAVA_FORMAT)
        
        trimTrailingWhitespace()
        endWithNewline()
        toggleOffOn()
    }
}

tasks.named("check") {
    dependsOn("spotlessCheck")
}

tasks.named("compileJava") {
    dependsOn("spotlessApply")
}

tasks.named("compileTestJava") {
    dependsOn("spotlessApply")
}

// ========================================
// Quality Tool Configuration File Extraction
// ========================================

// Load all resources during configuration phase so they're available
val checkstyleConfigContent = javaClass.getResourceAsStream("/checkstyle/checkstyle.xml")?.bufferedReader()?.readText()
    ?: throw IllegalStateException("Checkstyle configuration resource not found in plugin")
val suppressionsConfigContent = javaClass.getResourceAsStream("/checkstyle/suppressions.xml")?.bufferedReader()?.readText()
    ?: throw IllegalStateException("Checkstyle suppressions configuration resource not found in plugin")
val pmdConfigContent = javaClass.getResourceAsStream("/pmd/pmd-ruleset.xml")?.bufferedReader()?.readText()
    ?: throw IllegalStateException("PMD configuration resource not found in plugin")
val spotbugsConfigContent = javaClass.getResourceAsStream("/spotbugs/spotbugs-exclude.xml")?.bufferedReader()?.readText()
    ?: throw IllegalStateException("SpotBugs configuration resource not found in plugin")

val extractQualityToolConfigs = tasks.register("extractQualityToolConfigs") {
    group = "verification setup"
    description = "Extracts quality tool configuration files from plugin resources"
    
    val configDir = layout.buildDirectory.dir("gradle-plugins-config")
    outputs.dir(configDir)
    
    doLast {
        // Extract Checkstyle configs
        val checkstyleDir = configDir.get().dir("checkstyle").asFile
        checkstyleDir.mkdirs()
        File(checkstyleDir, "checkstyle.xml").writeText(checkstyleConfigContent)
        File(checkstyleDir, "suppressions.xml").writeText(suppressionsConfigContent)
        
        // Extract PMD config
        val pmdDir = configDir.get().dir("pmd").asFile
        pmdDir.mkdirs()
        File(pmdDir, "pmd-ruleset.xml").writeText(pmdConfigContent)
        
        // Extract SpotBugs config
        val spotbugsDir = configDir.get().dir("spotbugs").asFile
        spotbugsDir.mkdirs()
        File(spotbugsDir, "spotbugs-exclude.xml").writeText(spotbugsConfigContent)
    }
}

// ========================================
// Static Analysis Configuration
// ========================================

// Load Checkstyle configuration from plugin resources
val checkstyleConfigDir = layout.buildDirectory.dir("gradle-plugins-config/checkstyle").get().asFile
val checkstyleConfigFile = File(checkstyleConfigDir, "checkstyle.xml")
val suppressionsConfigFile = File(checkstyleConfigDir, "suppressions.xml")

configure<CheckstyleExtension> {
    toolVersion = Versions.CHECKSTYLE
    configFile = checkstyleConfigFile
    configDirectory.set(checkstyleConfigDir)
    isIgnoreFailures = false
    maxWarnings = 0
    maxErrors = 0
}

tasks.withType<Checkstyle>().configureEach {
    dependsOn(extractQualityToolConfigs)
    reports {
        xml.required.set(true)
        html.required.set(true)
        html.outputLocation.set(file("${layout.buildDirectory.get()}/reports/checkstyle/${name}.html"))
    }
    source = fileTree("src/main/java") + fileTree("src/test/java")
}

// Load PMD configuration from plugin resources
val pmdConfigDir = layout.buildDirectory.dir("gradle-plugins-config/pmd").get().asFile
val pmdConfigFile = File(pmdConfigDir, "pmd-ruleset.xml")

configure<PmdExtension> {
    toolVersion = Versions.PMD
    isConsoleOutput = true
    isIgnoreFailures = false
    ruleSets = listOf()
    ruleSetConfig = resources.text.fromFile(layout.buildDirectory.file("gradle-plugins-config/pmd/pmd-ruleset.xml"))
}

tasks.withType<Pmd>().configureEach {
    dependsOn(extractQualityToolConfigs)
    reports {
        xml.required.set(true)
        html.required.set(true)
        html.outputLocation.set(file("${layout.buildDirectory.get()}/reports/pmd/${name}.html"))
    }
    source = fileTree("src/main/java") + fileTree("src/test/java")
}

// Load SpotBugs configuration from plugin resources
val spotbugsConfigDir = layout.buildDirectory.dir("gradle-plugins-config/spotbugs").get().asFile
val spotbugsConfigFile = File(spotbugsConfigDir, "spotbugs-exclude.xml")

configure<com.github.spotbugs.snom.SpotBugsExtension> {
    toolVersion.set(Versions.SPOTBUGS)
    effort.set(com.github.spotbugs.snom.Effort.MAX)
    reportLevel.set(com.github.spotbugs.snom.Confidence.LOW)
    excludeFilter.set(layout.buildDirectory.file("gradle-plugins-config/spotbugs/spotbugs-exclude.xml").get().asFile)
    ignoreFailures.set(false)
}

tasks.named<com.github.spotbugs.snom.SpotBugsTask>("spotbugsMain") {
    dependsOn(extractQualityToolConfigs)
    reports.create("xml") {
        required.set(true)
        outputLocation.set(file("${layout.buildDirectory.get()}/reports/spotbugs/main.xml"))
    }
    reports.create("html") {
        required.set(true)
        setStylesheet("fancy-hist.xsl")
        outputLocation.set(file("${layout.buildDirectory.get()}/reports/spotbugs/main.html"))
    }
}

tasks.named<com.github.spotbugs.snom.SpotBugsTask>("spotbugsTest") {
    dependsOn(extractQualityToolConfigs)
    reports.create("xml") {
        required.set(true)
        outputLocation.set(file("${layout.buildDirectory.get()}/reports/spotbugs/test.xml"))
    }
    reports.create("html") {
        required.set(true)
        setStylesheet("fancy-hist.xsl")
        outputLocation.set(file("${layout.buildDirectory.get()}/reports/spotbugs/test.html"))
    }
}

// Configure spotbugsTestFixtures if it exists (for modules with testFixtures)
tasks.matching { it.name == "spotbugsTestFixtures" }.configureEach {
    dependsOn(extractQualityToolConfigs)
}

// Configure spotbugsIntegrationTest if it exists (for modules with integrationTest)
tasks.matching { it.name == "spotbugsIntegrationTest" }.configureEach {
    dependsOn(extractQualityToolConfigs)
}

tasks.register("lint") {
    group = "verification"
    description = "Runs all static analysis and linting tools (Checkstyle, PMD, SpotBugs)"
    dependsOn(
        "checkstyleMain",
        "checkstyleTest",
        "pmdMain",
        "pmdTest",
        "spotbugsMain",
        "spotbugsTest"
    )
}

tasks.named("check") {
    dependsOn("lint")
}

tasks.named("build") {
    dependsOn("test")
}

// ========================================
// Maven Publishing Configuration
// ========================================

// Validate and read required publishing metadata properties
fun requireProperty(key: String): String {
    return project.findProperty(key)?.toString()
        ?: throw GradleException("Required property '$key' not found in gradle.properties. Please add it to your project's gradle.properties file.")
}

val missingProperties = mutableListOf<String>()
fun checkProperty(key: String): String? {
    val value = project.findProperty(key)?.toString()
    if (value.isNullOrBlank()) {
        missingProperties.add(key)
    }
    return value
}

// Check all required properties
val pomUrl = checkProperty("project.pom.url")
val licenseName = checkProperty("project.pom.license.name")
val licenseUrl = checkProperty("project.pom.license.url")
val developerId = checkProperty("project.pom.developer.id")
val developerName = checkProperty("project.pom.developer.name")
val developerEmail = checkProperty("project.pom.developer.email")
val scmConnection = checkProperty("project.pom.scm.connection")
val scmDeveloperConnection = checkProperty("project.pom.scm.developerConnection")
val scmUrl = checkProperty("project.pom.scm.url")

if (missingProperties.isNotEmpty()) {
    throw GradleException(
        "Missing required publishing metadata properties in gradle.properties:\n" +
        missingProperties.joinToString("\n") { "  - $it" } +
        "\n\nPlease add these properties to your project's gradle.properties file."
    )
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            
            // Suppress warnings for testFixtures variants that cannot be mapped to Maven POM metadata
            // Maven doesn't understand Gradle's testFixtures concept, so these warnings are expected
            suppressPomMetadataWarningsFor("testFixturesApiElements")
            suppressPomMetadataWarningsFor("testFixturesRuntimeElements")
            
            // Artifact ID defaults to project name
            artifactId = project.name
            
            pom {
                name.set(project.name)
                description.set(project.description ?: project.name)
                url.set(pomUrl!!)
                
                licenses {
                    license {
                        name.set(licenseName!!)
                        url.set(licenseUrl!!)
                    }
                }
                
                developers {
                    developer {
                        id.set(developerId!!)
                        name.set(developerName!!)
                        email.set(developerEmail!!)
                    }
                }
                
                scm {
                    connection.set(scmConnection!!)
                    developerConnection.set(scmDeveloperConnection!!)
                    url.set(scmUrl!!)
                }
                
                // Ensure only direct dependencies are included in POM
                // The java component automatically handles this correctly
                // by only including dependencies from api/implementation configurations
                withXml {
                    // Remove any platform imports from the POM dependencies section
                    // These should only be in dependencyManagement
                    val depsNode = asNode().get("dependencies") as groovy.util.NodeList
                    if (depsNode.isNotEmpty()) {
                        val depsElement = depsNode[0] as groovy.util.Node
                        val depsList = depsElement.children().filterIsInstance<groovy.util.Node>()
                        depsList.filter { dep ->
                            val typeNode = dep.get("type") as groovy.util.NodeList
                            typeNode.isNotEmpty() && (typeNode[0] as groovy.util.Node).text() == "pom"
                        }.forEach { depsElement.remove(it) }
                    }
                }
            }
        }
    }
}

// Configure repositories AFTER project version is determined
// This ensures that the version check for SNAPSHOT routing happens after the version is set
afterEvaluate {
    publishing {
        repositories {
            // Publish to mavenLocal when PUBLISH_TO_MAVEN_LOCAL=true, otherwise to CodeArtifact
            if (System.getenv("PUBLISH_TO_MAVEN_LOCAL") == "true") {
                mavenLocal()
            } else {
                maven {
                    name = "CodeArtifact"
                    
                    // Get configuration from project properties (set by AwsEnvironmentPlugin)
                    val codeartifactDomain = project.findProperty("codeartifact.domain")?.toString() 
                        ?: System.getenv("CODEARTIFACT_DOMAIN") 
                        ?: ""
                    val codeartifactAccountId = project.findProperty("codeartifact.accountId")?.toString() 
                        ?: System.getenv("CODEARTIFACT_ACCOUNT_ID") 
                        ?: ""
                    val codeartifactRegion = project.findProperty("aws.region")?.toString() 
                        ?: System.getenv("AWS_REGION") 
                        ?: "us-east-1"
                    val codeartifactToken = project.findProperty("codeartifact.authToken")?.toString() 
                        ?: System.getenv("CODEARTIFACT_AUTH_TOKEN") 
                        ?: ""
                    
                    // Route to snapshots or releases repository based on version
                    // NOW version is correctly set after project evaluation
                    val repositoryName = if (version.toString().endsWith("-SNAPSHOT")) {
                        project.findProperty("codeartifact.javaSnapshotsRepository")?.toString() 
                            ?: System.getenv("JAVA_SNAPSHOTS_REPO_NAME") 
                            ?: "snapshots"
                    } else {
                        project.findProperty("codeartifact.javaRepository")?.toString() 
                            ?: System.getenv("JAVA_REPO_NAME") 
                            ?: "releases"
                    }
                    
                    url = uri("https://$codeartifactDomain-$codeartifactAccountId.d.codeartifact.$codeartifactRegion.amazonaws.com/maven/$repositoryName/")
                    
                    credentials {
                        username = "aws"
                        password = codeartifactToken
                    }
                }
            }
        }
    }
}

// Only enable publishing if CodeArtifact token is available OR publishing to mavenLocal
tasks.withType<PublishToMavenRepository>().configureEach {
    val codeartifactToken = project.findProperty("codeartifact.authToken")?.toString() 
        ?: System.getenv("CODEARTIFACT_AUTH_TOKEN") 
        ?: ""
    val publishToMavenLocal = System.getenv("PUBLISH_TO_MAVEN_LOCAL") == "true"
    
    // Enable publishing if either CodeArtifact token is present OR publishing to mavenLocal
    enabled = codeartifactToken.isNotEmpty() || publishToMavenLocal
    
    doFirst {
        if (publishToMavenLocal) {
            println("Publishing ${project.group}:${project.name}:${project.version} to local Maven repository")
        } else {
            val repoType = if (project.version.toString().endsWith("-SNAPSHOT")) "snapshots" else "releases"
            println("Publishing ${project.group}:${project.name}:${project.version} to CodeArtifact $repoType repository")
        }
    }
}
