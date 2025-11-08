package com.lockbox.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.File

/**
 * Generates a redirect HTML file that points to the latest documentation version.
 *
 * This task creates a simple HTML file that automatically redirects browsers to the
 * current version of the documentation. This allows maintaining a stable URL
 * (e.g., `/site/lockbox-framework/latest/`) that always points to the most recent
 * documentation without manual updates.
 *
 * **Redirect Strategy:**
 * The generated HTML uses multiple redirect methods for maximum compatibility:
 * - HTTP meta refresh tag for browser compatibility
 * - Canonical link for search engines
 * - JavaScript fallback for clients that disable meta refresh
 *
 * **Configuration Cache Compatible:** Yes
 * All inputs are captured at configuration time, making this task fully compatible
 * with Gradle's configuration cache.
 *
 * **Usage:**
 * ```kotlin
 * tasks.register<WriteLatestRedirectTask>("writeLatestRedirect") {
 *     projectVersion.set("1.0.0")
 *     artifactType.set("site")
 *     projectSlug.set("lockbox-framework")
 *     s3BasePrefix.set("site/lockbox-framework")
 *     outputDir.set(layout.buildDirectory.dir("docsUpload/site/lockbox-framework/latest"))
 * }
 * ```
 *
 * @see GenerateSiteTask
 * @author Lockbox AI Engineering
 */
abstract class WriteLatestRedirectTask : DefaultTask() {
    /**
     * The current version of the project.
     * Used to construct the redirect URL target.
     */
    @get:Input
    abstract val projectVersion: Property<String>

    /**
     * The artifact type (e.g., "site", "javadoc", "reports").
     * Used to construct the redirect URL path.
     */
    @get:Input
    abstract val artifactType: Property<String>

    /**
     * The project slug used in the documentation URL.
     * Used to construct the redirect URL path.
     */
    @get:Input
    abstract val projectSlug: Property<String>

    /**
     * The S3 base prefix for documentation storage.
     * Used for logging and reference purposes.
     */
    @get:Input
    abstract val s3BasePrefix: Property<String>

    /**
     * The output directory where the redirect index.html will be written.
     * Typically points to the "latest" directory in the documentation structure.
     */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    /**
     * Executes the redirect file generation task.
     *
     * Creates an index.html file in the output directory that redirects to the
     * versioned documentation URL. The redirect uses multiple methods for
     * maximum compatibility across different browsers and clients.
     */
    @TaskAction
    fun writeRedirect() {
        val destDir = outputDir.get().asFile
        destDir.mkdirs()
        
        val file = File(destDir, "index.html")
        val version = projectVersion.get()
        val artifactTypeVal = artifactType.get()
        val projectSlugVal = projectSlug.get()
        
        file.writeText(
            """
            <!doctype html>
            <html lang="en">
            <head>
                <meta charset="utf-8">
                <title>Redirecting to Latest Documentation</title>
                <meta http-equiv="refresh" content="0; url=/$artifactTypeVal/$projectSlugVal/$version/">
                <link rel="canonical" href="/$artifactTypeVal/$projectSlugVal/$version/">
                <script>location.replace("/$artifactTypeVal/$projectSlugVal/$version/");</script>
            </head>
            <body>
                <p>Redirecting to <a href="/$artifactTypeVal/$projectSlugVal/$version/">latest documentation</a>…</p>
            </body>
            </html>
            """.trimIndent()
        )
        logger.lifecycle("Created latest redirect: ${file.absolutePath}")
    }
}
