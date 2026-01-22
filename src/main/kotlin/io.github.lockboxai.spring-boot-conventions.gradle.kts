import com.lockbox.gradle.utils.FrameworkDependencyManager

/**
 * Lockbox Spring Boot Conventions Plugin
 * 
 * This convention plugin defines shared configuration for Spring Boot applications.
 * It builds on top of lockbox.gradle.plugins.java-conventions and adds:
 * - Lombok plugin application
 * - Spring Dependency Management for automatic version control
 * - Standard module dependencies (javax-annotations)
 * 
 * Spring Boot applications using this plugin should explicitly apply:
 * - org.springframework.boot plugin
 * 
 * Dependency versions are automatically managed by this plugin:
 * - In composite builds: Parses lockbox-framework/gradle/libs.versions.toml directly
 * - In published builds: Imports com.lockbox:framework-platform BOM
 * 
 * This uses Spring Dependency Management Plugin's Maven-style dependency management
 * which avoids Gradle's java-platform variant issues in composite builds.
 */

plugins {
    // Apply the base Java conventions plugin first
    id("io.github.lockboxai.java-conventions")
    
    // Apply Lombok plugin for all Spring Boot applications
    // This is applied here (not in java-conventions) to avoid Gradle 9.2.0 Tooling API issues
    id("io.freefair.lombok")
    
    // Apply Spring Dependency Management plugin for Maven-style version management
    // This avoids Gradle's java-platform variant issues in composite builds
    id("io.spring.dependency-management")
}

// ========================================
// Dependency Version Management
// ========================================
// Configure dependency management based on build context.
// For Spring Boot applications, versions are automatically managed via
// Spring Dependency Management Plugin, which handles both composite builds
// and published artifact scenarios seamlessly.

FrameworkDependencyManager.configureDependencyManagement(project, logger)

// ========================================
// Standard Module Dependencies
// ========================================
// Versions are automatically managed by Spring Dependency Management plugin

dependencies {
    // JavaX Annotations (required for Lombok @Generated annotation)
    compileOnly("javax.annotation:javax.annotation-api")
    
    // Lombok (required for annotation processing)
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    
    // Note: Spring Boot applications should declare their own implementation dependencies
    // (e.g., spring-boot-starter-web, spring-boot-starter-data-jpa) as needed.
    // This keeps applications lightweight and only includes what they actually use.
}
