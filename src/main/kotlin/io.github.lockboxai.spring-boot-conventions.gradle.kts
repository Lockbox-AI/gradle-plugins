import com.lockbox.gradle.utils.FrameworkPlatformResolver

/**
 * Lockbox Spring Boot Conventions Plugin
 * 
 * This convention plugin defines shared configuration for Spring Boot applications.
 * It builds on top of lockbox.gradle.plugins.java-conventions and adds:
 * - Lombok plugin application
 * - Standard module dependencies (javax-annotations, commons-lang3, test frameworks)
 * - Framework-platform BOM for dependency version management
 * 
 * Spring Boot applications using this plugin should explicitly apply:
 * - org.springframework.boot plugin
 * - io.spring.dependency-management plugin
 * 
 * This keeps the convention plugin generic and allows consuming projects to choose
 * their Spring Boot version independently.
 */

plugins {
    // Apply the base Java conventions plugin first
    id("io.github.lockboxai.java-conventions")
    
    // Apply Lombok plugin for all Spring Boot applications
    // This is applied here (not in java-conventions) to avoid Gradle 9.2.0 Tooling API issues
    id("io.freefair.lombok")
}

// ========================================
// Standard Module Dependencies
// ========================================

dependencies {
    // Import the framework platform for version management
    // 
    // The plugin handles three scenarios:
    // 1. Building lockbox-framework itself - use project() reference
    // 2. Composite build including lockbox-framework - platform resolved transitively
    // 3. External consumers - platform added via Maven coordinate
    //
    val isFrameworkBuild = rootProject.name == "lockbox-framework"
    val isCompositeBuildWithFramework = rootProject.findProject(":lockbox-framework") != null
    
    if (isFrameworkBuild) {
        // Building lockbox-framework itself - use project reference
        implementation(platform(project(":framework-platform")))
    } else if (!isCompositeBuildWithFramework) {
        // External consumer without composite build - add platform via Maven coordinate
        // Version resolution handled by FrameworkPlatformResolver (version catalog -> env var -> property)
        implementation(platform(FrameworkPlatformResolver.getMavenCoordinate(project)))
    }
    
    // JavaX Annotations (required for Lombok @Generated annotation)
    // Version is managed by framework-platform BOM
    compileOnly("javax.annotation:javax.annotation-api")
    
    // Lombok (required for annotation processing)
    // Version is managed by framework-platform BOM
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    
    // Note: Spring Boot applications should declare their own implementation dependencies
    // (e.g., spring-boot-starter-web, spring-boot-starter-data-jpa) as needed.
    // This keeps applications lightweight and only includes what they actually use.
}
