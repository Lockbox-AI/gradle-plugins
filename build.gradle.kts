/**
 * Lockbox Gradle Plugins Build Configuration
 * 
 * Builds and publishes reusable Gradle plugins for Java/Kotlin projects with AWS integration.
 * 
 * Plugins Provided:
 * - aws-environment: AWS authentication, role assumption, and environment setup
 * - codeartifact-repositories: CodeArtifact Maven repository configuration
 * - codeartifact-repositories-settings: CodeArtifact plugin repository configuration (settings)
 * - java-conventions: Base Java configuration with quality tools and testing
 * - module-conventions: Lockbox module configuration (extends java-conventions)
 * - spring-boot-conventions: Spring Boot application configuration
 * - spring-shell-conventions: Spring Shell CLI application configuration
 */

import java.time.Instant

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "2.0.0"
}

group = project.property("group") as String
description = "Lockbox Gradle Plugins - Reusable build conventions and AWS integration"

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    // AWS SDK BOM for version management
    implementation(platform(libs.aws.sdk.bom))
    
    // AWS SDK dependencies
    implementation(libs.aws.s3)
    implementation(libs.aws.apache.client)
    implementation(libs.aws.codeartifact)
    implementation(libs.aws.sts)
    implementation(libs.aws.ssm)
    
    // Gradle plugin dependencies for convention plugins
    implementation(libs.spotless.plugin)
    implementation(libs.lombok.plugin)
    implementation(libs.spotbugs.plugin)
    implementation(libs.spring.boot.plugin)
    implementation(libs.dependency.management.plugin)
}

kotlin {
    jvmToolchain(21)
}

// ========================================
// Gradle Plugin Declarations
// ========================================
/**
 * Declares plugins for publication to Gradle Plugin Portal.
 * 
 * Programmatic Gradle plugins (Kotlin classes) are declared explicitly here.
 * Precompiled script plugins (.gradle.kts files) are automatically discovered
 * by the kotlin-dsl plugin and registered with their metadata from the file headers.
 */
gradlePlugin {
    // Project-wide metadata (applies to all plugins)
    website = "https://github.com/Lockbox-AI/gradle-plugins"
    vcsUrl = "https://github.com/Lockbox-AI/gradle-plugins.git"
    
    plugins {
        // ========================================
        // AWS Integration Plugins (Kotlin Classes)
        // ========================================
        create("awsEnvironment") {
            id = "io.github.lockboxai.aws-environment"
            displayName = "Lockbox AWS Environment"
            description = "Provides AWS authentication, role assumption, and environment setup for Gradle builds. Integrates with AWS CodeArtifact and SSM Parameter Store."
            implementationClass = "com.lockbox.gradle.AwsEnvironmentPlugin"
            tags.set(listOf("aws", "authentication", "codeartifact", "sts", "ssm"))
        }
        create("codeartifactRepositories") {
            id = "io.github.lockboxai.codeartifact-repositories"
            displayName = "Lockbox CodeArtifact Repositories"
            description = "Automatically configures Maven repositories for AWS CodeArtifact with authentication. Apply to projects to resolve dependencies from CodeArtifact."
            implementationClass = "com.lockbox.gradle.CodeArtifactRepositoriesPlugin"
            tags.set(listOf("aws", "codeartifact", "repositories", "maven", "dependencies"))
        }
        create("codeartifactRepositoriesSettings") {
            id = "io.github.lockboxai.codeartifact-repositories-settings"
            displayName = "Lockbox CodeArtifact Repositories (Settings)"
            description = "Configures plugin repositories for AWS CodeArtifact in settings.gradle.kts. Apply in settings to resolve Gradle plugins from CodeArtifact."
            implementationClass = "com.lockbox.gradle.CodeArtifactRepositoriesSettingsPlugin"
            tags.set(listOf("aws", "codeartifact", "repositories", "plugins", "settings"))
        }
        
        // Note: Precompiled script convention plugins (*.gradle.kts) are automatically
        // registered by the kotlin-dsl plugin. Their metadata is configured below
        // in an afterEvaluate block after they are registered.
    }
}

// ========================================
// Configure Precompiled Script Plugin Metadata
// ========================================
// Precompiled script plugins are auto-registered by kotlin-dsl plugin.
// We configure their display metadata here for Gradle Plugin Portal publication.
afterEvaluate {
    gradlePlugin.plugins.named("io.github.lockboxai.java-conventions") {
        displayName = "Lockbox Java Conventions"
        description = "Base Java configuration with quality tools (Checkstyle, PMD, SpotBugs, Spotless), testing setup (JUnit 5, Mockito, AssertJ), JaCoCo coverage, and Maven publishing. Provides consistent Java 21 configuration across projects."
        tags.set(listOf("java", "conventions", "quality", "testing", "jacoco", "checkstyle", "pmd", "spotbugs"))
    }
    gradlePlugin.plugins.named("io.github.lockboxai.module-conventions") {
        displayName = "Lockbox Module Conventions"
        description = "Extends java-conventions with Lockbox-specific module configuration. Includes Lombok support, additional quality checks, and framework-specific settings for building Lockbox framework modules."
        tags.set(listOf("java", "conventions", "module", "lombok", "framework"))
    }
    gradlePlugin.plugins.named("io.github.lockboxai.spring-boot-conventions") {
        displayName = "Lockbox Spring Boot Conventions"
        description = "Spring Boot application configuration extending java-conventions. Configures Spring Boot Gradle plugin, dependency management, executable JAR/WAR packaging, and Spring-specific testing setup."
        tags.set(listOf("spring-boot", "spring", "conventions", "java", "application"))
    }
    gradlePlugin.plugins.named("io.github.lockboxai.spring-shell-conventions") {
        displayName = "Lockbox Spring Shell Conventions"
        description = "Spring Shell CLI application configuration extending spring-boot-conventions. Configures Spring Shell dependencies and CLI-specific packaging for command-line applications."
        tags.set(listOf("spring-shell", "spring-boot", "cli", "conventions", "java"))
    }
}

// ========================================
// JAR Manifest Configuration
// ========================================
tasks.withType<Jar>().configureEach {
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "Lockbox AI",
            "Built-By" to System.getProperty("user.name"),
            "Built-JDK" to System.getProperty("java.version"),
            "Build-Timestamp" to Instant.now().toString()
        )
    }
}

// ========================================
// Publishing Configuration
// ========================================
java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("pluginMaven") {
            // The java-gradle-plugin automatically creates publications for each plugin
            // We add this publication for consistency with our convention patterns
        }
    }
}

// Configure repositories AFTER project version is determined
// This ensures version-based routing happens after version is set
afterEvaluate {
    publishing {
        repositories {
            // Always add CodeArtifact repository when token is available
            val codeartifactToken = System.getenv("CODEARTIFACT_AUTH_TOKEN") ?: ""
            if (codeartifactToken.isNotEmpty()) {
                maven {
                    name = "CodeArtifact"
                    
                    // Get configuration from environment variables (set by publish.sh script)
                    val codeartifactDomain = System.getenv("CODEARTIFACT_DOMAIN") ?: ""
                    val codeartifactAccountId = System.getenv("CODEARTIFACT_ACCOUNT_ID") ?: ""
                    val codeartifactRegion = System.getenv("CODEARTIFACT_REGION") ?: "us-east-1"
                    
                    // Route to releases repository (plugins should always be releases, not snapshots)
                    val repositoryName = System.getenv("CODEARTIFACT_JAVA_REPOSITORY") ?: "releases"
                    
                    url = uri("https://$codeartifactDomain-$codeartifactAccountId.d.codeartifact.$codeartifactRegion.amazonaws.com/maven/$repositoryName/")
                    
                    credentials {
                        username = "aws"
                        password = codeartifactToken
                    }
                }
            }
            
            // Add mavenLocal when PUBLISH_TO_MAVEN_LOCAL=true
            if (System.getenv("PUBLISH_TO_MAVEN_LOCAL") == "true") {
                mavenLocal()
            }
        }
    }
}

// Only enable publishing if CodeArtifact token is available OR publishing to mavenLocal
tasks.withType<PublishToMavenRepository>().configureEach {
    val codeartifactToken = System.getenv("CODEARTIFACT_AUTH_TOKEN") ?: ""
    val publishToMavenLocal = System.getenv("PUBLISH_TO_MAVEN_LOCAL") == "true"
    
    // Enable publishing if either CodeArtifact token is present OR publishing to mavenLocal
    enabled = codeartifactToken.isNotEmpty() || publishToMavenLocal
    
    doFirst {
        if (publishToMavenLocal) {
            println("Publishing ${project.group}:${project.name}:${project.version} to local Maven repository")
        } else {
            println("Publishing ${project.group}:${project.name}:${project.version} to CodeArtifact releases repository")
        }
    }
}
