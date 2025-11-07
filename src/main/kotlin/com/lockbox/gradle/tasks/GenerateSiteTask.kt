package com.lockbox.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Generates a comprehensive project site with aggregated reports and metrics.
 *
 * This task orchestrates the collection and presentation of all project quality metrics
 * into a single unified HTML site. It extracts metrics from various build reports
 * (JaCoCo, Checkstyle, PMD, SpotBugs, Test results) and generates an interactive
 * dashboard with visual representations of code quality.
 *
 * **Metrics Extracted:**
 * - JaCoCo: Line, branch, and instruction coverage percentages
 * - Checkstyle: Code style violations (errors, warnings, infos)
 * - PMD: Static analysis issues by priority level
 * - SpotBugs: Bug detection results by severity
 * - Test Results: Test execution statistics (passed, failed, skipped)
 *
 * **Task Dependencies:**
 * This task depends on all report generation tasks to ensure metrics are available:
 * - test, jacocoTestReport, checkstyleMain, checkstyleTest
 * - pmdMain, pmdTest, spotbugsMain, spotbugsTest
 * - javadoc, projectReport
 *
 * **Configuration Cache Compatible:** Yes
 * All inputs are captured at configuration time, making this task fully compatible
 * with Gradle's configuration cache for improved build performance.
 *
 * **Usage:**
 * ```kotlin
 * tasks.register<GenerateSiteTask>("generateSite") {
 *     projectName.set(project.name)
 *     projectVersion.set(project.version.toString())
 *     gradleVersion.set(gradle.gradleVersion)
 *     buildDir.set(project.layout.buildDirectory)
 *     siteDir.set(project.layout.buildDirectory.dir("site"))
 * }
 * ```
 *
 * @see com.lockbox.gradle.utils.S3DocumentationUploader
 * @author Lockbox AI Engineering
 */
abstract class GenerateSiteTask : DefaultTask() {
    /**
     * The name of the project being documented.
     * Used in the HTML header and page title.
     */
    @get:Input
    abstract val projectName: Property<String>

    /**
     * The version of the project being documented.
     * Displayed in the site header and project information section.
     */
    @get:Input
    abstract val projectVersion: Property<String>

    /**
     * The Gradle version used for the build.
     * Captured at configuration time to avoid runtime project access.
     * Displayed in the project information section.
     */
    @get:Input
    abstract val gradleVersion: Property<String>

    /**
     * The name of the module being documented (for multi-module projects).
     * When set, generates a detailed site in a subdirectory named after the module.
     * When not set (root project), generates an aggregated index linking to all module sites.
     */
    @get:Input
    @get:Optional
    abstract val moduleName: Property<String>

    /**
     * The build directory containing all generated reports.
     * Used to locate JaCoCo, Checkstyle, PMD, SpotBugs, and test reports.
     */
    @get:InputDirectory
    abstract val buildDir: DirectoryProperty

    /**
     * The output directory where the generated site will be written.
     * Contains the index.html and all copied report directories.
     */
    @get:OutputDirectory
    abstract val siteDir: DirectoryProperty

    /**
     * Executes the site generation task.
     *
     * For multi-module projects:
     * - When moduleName is set: Generates a detailed site in a module subdirectory
     * - When moduleName is not set: Generates an aggregated index linking to all modules
     *
     * For single-module projects:
     * - Generates a detailed site at the root level
     *
     * This method:
     * 1. Determines the context (root vs module)
     * 2. Extracts metrics from all available XML reports (if module-level)
     * 3. Copies report directories to the appropriate location
     * 4. Generates the appropriate index.html (aggregated or detailed)
     *
     * @throws IllegalStateException if the HTML template resource is not found
     */
    @TaskAction
    fun generate() {
        val site = siteDir.get().asFile
        val moduleNameVal = moduleName.orNull
        
        if (moduleNameVal != null) {
            // Module-level: generate detailed site in subdirectory
            logger.lifecycle("Generating site for module: $moduleNameVal")
            val moduleSite = site.resolve(moduleNameVal)
            if (moduleSite.exists()) moduleSite.deleteRecursively()
            moduleSite.mkdirs()
            
            generateDetailedSite(moduleSite)
            logger.lifecycle("✓ Module site generated: $moduleNameVal")
        } else {
            // Root-level: check for existing modules or generate single-module site
            site.mkdirs()
            val modules = site.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.map { it.name } ?: emptyList()
            
            if (modules.isNotEmpty()) {
                // Multi-module project: generate aggregated index
                logger.lifecycle("Generating aggregated site index for ${modules.size} module(s)")
                generateAggregatedIndexHtml(site, modules)
                logger.lifecycle("✓ Aggregated site generated successfully!")
            } else {
                // Single-module project: generate detailed site at root
                logger.lifecycle("Generating site for single-module project")
                if (site.exists()) site.deleteRecursively()
                site.mkdirs()
                generateDetailedSite(site)
                logger.lifecycle("✓ Project site generated successfully!")
            }
        }
    }

    /**
     * Generates a detailed site with metrics and reports.
     *
     * Extracts metrics from various report files (JaCoCo, Checkstyle, PMD, SpotBugs, tests),
     * copies report directories, and generates a comprehensive index.html dashboard.
     *
     * @param targetDir The directory where the site should be generated
     */
    private fun generateDetailedSite(targetDir: File) {
        val buildDirFile = buildDir.get().asFile
        val jacocoXml = File(buildDirFile, "reports/jacoco/test/jacocoTestReport.xml")
        val jacocoMetrics = extractJaCoCoMetrics(jacocoXml)

        val checkstyleMainXml = File(buildDirFile, "reports/checkstyle/main.xml")
        val checkstyleTestXml = File(buildDirFile, "reports/checkstyle/test.xml")
        val checkstyleMetrics = extractCheckstyleMetrics(checkstyleMainXml, checkstyleTestXml)

        val pmdMainXml = File(buildDirFile, "reports/pmd/main.xml")
        val pmdTestXml = File(buildDirFile, "reports/pmd/test.xml")
        val pmdMetrics = extractPmdMetrics(pmdMainXml, pmdTestXml)

        val spotbugsMainXml = File(buildDirFile, "reports/spotbugs/main.xml")
        val spotbugsTestXml = File(buildDirFile, "reports/spotbugs/test.xml")
        val spotbugsMetrics = extractSpotBugsMetrics(spotbugsMainXml, spotbugsTestXml)

        val testResultsDir = File(buildDirFile, "test-results/test")
        val testMetrics = extractTestMetrics(testResultsDir)

        copyReports(buildDirFile, targetDir)
        generateIndexHtml(targetDir, jacocoMetrics, checkstyleMetrics, pmdMetrics, spotbugsMetrics, testMetrics)
    }

    /**
     * Parses an XML file and returns its root element.
     *
     * @param file The XML file to parse
     * @return The root element of the XML document, or null if the file doesn't exist or parsing fails
     */
    private fun parseXmlFile(file: File): Element? {
        if (!file.exists()) return null
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(file)
            doc.documentElement
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extracts code coverage metrics from a JaCoCo XML report.
     *
     * Extracts the aggregated totals for:
     * - Line coverage percentage
     * - Branch coverage percentage
     * - Instruction coverage percentage
     *
     * Uses robust DOM parsing to reliably extract metrics from the entire XML document,
     * ensuring consistency with other metric extraction methods.
     *
     * @param xmlFile The JaCoCo XML report file
     * @return A map with keys: linesCoverage, branchesCoverage, instructionsCoverage
     *         Values are formatted as percentages (e.g., "85.5")
     */
    private fun extractJaCoCoMetrics(xmlFile: File): Map<String, String> {
        val metrics = mutableMapOf<String, String>()
        val root = parseXmlFile(xmlFile) ?: return metrics
        
        // Find the last counter element for each type (aggregated totals)
        val counterNodes = root.getElementsByTagName("counter")
        var lineCounter: Element? = null
        var branchCounter: Element? = null
        var instructionCounter: Element? = null
        
        for (i in 0 until counterNodes.length) {
            val counter = counterNodes.item(i) as Element
            when (counter.getAttribute("type")) {
                "LINE" -> lineCounter = counter
                "BRANCH" -> branchCounter = counter
                "INSTRUCTION" -> instructionCounter = counter
            }
        }
        
        // Extract line coverage
        lineCounter?.let { counter ->
            val missed = counter.getAttribute("missed").toIntOrNull() ?: 0
            val covered = counter.getAttribute("covered").toIntOrNull() ?: 0
            val total = missed + covered
            if (total > 0) {
                metrics["linesCoverage"] = String.format("%.1f", (covered.toDouble() / total) * 100)
            }
        }
        
        // Extract branch coverage
        branchCounter?.let { counter ->
            val missed = counter.getAttribute("missed").toIntOrNull() ?: 0
            val covered = counter.getAttribute("covered").toIntOrNull() ?: 0
            val total = missed + covered
            if (total > 0) {
                metrics["branchesCoverage"] = String.format("%.1f", (covered.toDouble() / total) * 100)
            }
        }
        
        // Extract instruction coverage
        instructionCounter?.let { counter ->
            val missed = counter.getAttribute("missed").toIntOrNull() ?: 0
            val covered = counter.getAttribute("covered").toIntOrNull() ?: 0
            val total = missed + covered
            if (total > 0) {
                metrics["instructionsCoverage"] = String.format("%.1f", (covered.toDouble() / total) * 100)
            }
        }
        
        return metrics
    }

    /**
     * Extracts code style violation metrics from Checkstyle XML reports.
     *
     * Aggregates violations from both main and test source reports by severity:
     * - errors: Code style errors
     * - warnings: Code style warnings
     * - infos: Code style information messages
     * - total: Total violations across all severities
     *
     * @param mainXml The Checkstyle XML report for main sources
     * @param testXml The Checkstyle XML report for test sources
     * @return A map with violation counts by severity
     */
    private fun extractCheckstyleMetrics(mainXml: File, testXml: File): Map<String, Int> {
        val metrics = mutableMapOf("errors" to 0, "warnings" to 0, "infos" to 0, "total" to 0)
        listOf(mainXml, testXml).forEach { file ->
            val root = parseXmlFile(file) ?: return@forEach
            val errorNodes = root.getElementsByTagName("error")
            for (i in 0 until errorNodes.length) {
                val error = errorNodes.item(i) as Element
                when (error.getAttribute("severity")) {
                    "error" -> metrics["errors"] = metrics["errors"]!! + 1
                    "warning" -> metrics["warnings"] = metrics["warnings"]!! + 1
                    "info" -> metrics["infos"] = metrics["infos"]!! + 1
                }
                metrics["total"] = metrics["total"]!! + 1
            }
        }
        return metrics
    }

    /**
     * Extracts static analysis metrics from PMD XML reports.
     *
     * Aggregates violations from both main and test source reports by priority:
     * - priority1: High priority issues
     * - priority2: Medium-high priority issues
     * - priority3: Medium priority issues
     * - priority4: Low-medium priority issues
     * - priority5: Low priority issues
     * - total: Total issues across all priorities
     *
     * @param mainXml The PMD XML report for main sources
     * @param testXml The PMD XML report for test sources
     * @return A map with issue counts by priority level
     */
    private fun extractPmdMetrics(mainXml: File, testXml: File): Map<String, Int> {
        val metrics = mutableMapOf("priority1" to 0, "priority2" to 0, "priority3" to 0, "priority4" to 0, "priority5" to 0, "total" to 0)
        listOf(mainXml, testXml).forEach { file ->
            val root = parseXmlFile(file) ?: return@forEach
            val violationNodes = root.getElementsByTagName("violation")
            for (i in 0 until violationNodes.length) {
                val violation = violationNodes.item(i) as Element
                val priority = violation.getAttribute("priority").toIntOrNull() ?: 3
                metrics["priority$priority"] = metrics["priority$priority"]!! + 1
                metrics["total"] = metrics["total"]!! + 1
            }
        }
        return metrics
    }

    /**
     * Extracts bug detection metrics from SpotBugs XML reports.
     *
     * Aggregates bugs from both main and test source reports by severity:
     * - high: High severity bugs (priority 1)
     * - medium: Medium severity bugs (priority 2)
     * - low: Low severity bugs (priority 3)
     * - total: Total bugs across all severities
     *
     * @param mainXml The SpotBugs XML report for main sources
     * @param testXml The SpotBugs XML report for test sources
     * @return A map with bug counts by severity level
     */
    private fun extractSpotBugsMetrics(mainXml: File, testXml: File): Map<String, Int> {
        val metrics = mutableMapOf("high" to 0, "medium" to 0, "low" to 0, "total" to 0)
        listOf(mainXml, testXml).forEach { file ->
            val root = parseXmlFile(file) ?: return@forEach
            val bugNodes = root.getElementsByTagName("BugInstance")
            for (i in 0 until bugNodes.length) {
                val bug = bugNodes.item(i) as Element
                when (bug.getAttribute("priority")) {
                    "1" -> metrics["high"] = metrics["high"]!! + 1
                    "2" -> metrics["medium"] = metrics["medium"]!! + 1
                    "3" -> metrics["low"] = metrics["low"]!! + 1
                }
                metrics["total"] = metrics["total"]!! + 1
            }
        }
        return metrics
    }

    /**
     * Extracts test execution metrics from JUnit XML reports.
     *
     * Aggregates test results from all test suites:
     * - total: Total number of tests
     * - passed: Number of passed tests
     * - failed: Number of failed tests
     * - skipped: Number of skipped tests
     *
     * @param testResultsDir The directory containing JUnit XML test result files
     * @return A map with test execution statistics
     */
    private fun extractTestMetrics(testResultsDir: File): Map<String, Int> {
        val metrics = mutableMapOf("total" to 0, "passed" to 0, "failed" to 0, "skipped" to 0)
        if (!testResultsDir.exists()) return metrics
        testResultsDir.listFiles { file -> file.extension == "xml" }?.forEach { file ->
            val root = parseXmlFile(file) ?: return@forEach
            val testsuites = if (root.tagName == "testsuite") listOf(root) else {
                val suiteNodes = root.getElementsByTagName("testsuite")
                (0 until suiteNodes.length).map { suiteNodes.item(it) as Element }
            }
            testsuites.forEach { testsuite ->
                metrics["total"] = metrics["total"]!! + (testsuite.getAttribute("tests").toIntOrNull() ?: 0)
                metrics["failed"] = metrics["failed"]!! + (testsuite.getAttribute("failures").toIntOrNull() ?: 0)
                metrics["skipped"] = metrics["skipped"]!! + (testsuite.getAttribute("skipped").toIntOrNull() ?: 0)
            }
        }
        metrics["passed"] = metrics["total"]!! - metrics["failed"]!! - metrics["skipped"]!!
        return metrics
    }

    /**
     * Copies all generated report directories to the site output directory.
     *
     * Copies the following reports if they exist:
     * - JavaDoc API documentation
     * - JaCoCo code coverage reports
     * - Test execution reports
     * - Checkstyle analysis reports
     * - PMD analysis reports
     * - SpotBugs analysis reports
     * - Project dependency reports
     *
     * @param buildDirFile The build directory containing source reports
     * @param siteDir The site output directory where reports will be copied
     */
    private fun copyReports(buildDirFile: File, siteDir: File) {
        val reportsToCopy = mapOf(
            File(buildDirFile, "docs/javadoc") to File(siteDir, "javadoc"),
            File(buildDirFile, "reports/jacoco/test/html") to File(siteDir, "coverage"),
            File(buildDirFile, "reports/tests/test") to File(siteDir, "tests"),
            File(buildDirFile, "reports/checkstyle") to File(siteDir, "checkstyle"),
            File(buildDirFile, "reports/pmd") to File(siteDir, "pmd"),
            File(buildDirFile, "reports/spotbugs") to File(siteDir, "spotbugs"),
            File(buildDirFile, "reports/project") to File(siteDir, "reports/project")
        )
        reportsToCopy.forEach { (source, dest) ->
            if (source.exists()) {
                logger.lifecycle("Copying ${source.name} reports...")
                source.copyRecursively(dest, overwrite = true)
            }
        }
    }

    /**
     * Generates the main index.html file with metrics dashboard and report links.
     *
     * Loads the HTML template from resources and substitutes all metric values
     * and project information. Creates an interactive dashboard with:
     * - Project information (name, version, build date, Java/Gradle versions)
     * - Quality metrics cards (coverage, violations, bugs, test results)
     * - Links to detailed reports
     *
     * @param siteDir The output directory where index.html will be written
     * @param jacocoMetrics Code coverage metrics from JaCoCo
     * @param checkstyleMetrics Code style metrics from Checkstyle
     * @param pmdMetrics Static analysis metrics from PMD
     * @param spotbugsMetrics Bug detection metrics from SpotBugs
     * @param testMetrics Test execution metrics
     * @throws IllegalStateException if the HTML template resource cannot be loaded
     */
    private fun generateIndexHtml(siteDir: File, jacocoMetrics: Map<String, String>, checkstyleMetrics: Map<String, Int>, pmdMetrics: Map<String, Int>, spotbugsMetrics: Map<String, Int>, testMetrics: Map<String, Int>) {
        val template = javaClass.getResourceAsStream("/templates/site-index.html.template")?.bufferedReader()?.readText()
            ?: throw IllegalStateException("HTML template not found")
        
        val buildDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val javaVersion = System.getProperty("java.version")

        val html = template
            .replace("{{projectName}}", projectName.get())
            .replace("{{projectVersion}}", projectVersion.get())
            .replace("{{buildDate}}", buildDate)
            .replace("{{javaVersion}}", javaVersion)
            .replace("{{gradleVersion}}", gradleVersion.get())
            .replace("{{linesCoverage}}", jacocoMetrics["linesCoverage"] ?: "0.0")
            .replace("{{branchesCoverage}}", jacocoMetrics["branchesCoverage"] ?: "0.0")
            .replace("{{instructionsCoverage}}", jacocoMetrics["instructionsCoverage"] ?: "0.0")
            .replace("{{checkstyleTotal}}", (checkstyleMetrics["total"] ?: 0).toString())
            .replace("{{checkstyleErrors}}", (checkstyleMetrics["errors"] ?: 0).toString())
            .replace("{{checkstyleWarnings}}", (checkstyleMetrics["warnings"] ?: 0).toString())
            .replace("{{checkstyleInfos}}", (checkstyleMetrics["infos"] ?: 0).toString())
            .replace("{{pmdTotal}}", (pmdMetrics["total"] ?: 0).toString())
            .replace("{{pmdPriority1}}", (pmdMetrics["priority1"] ?: 0).toString())
            .replace("{{pmdPriority2}}", (pmdMetrics["priority2"] ?: 0).toString())
            .replace("{{pmdPriority3}}", (pmdMetrics["priority3"] ?: 0).toString())
            .replace("{{pmdPriority45}}", ((pmdMetrics["priority4"] ?: 0) + (pmdMetrics["priority5"] ?: 0)).toString())
            .replace("{{spotbugsTotal}}", (spotbugsMetrics["total"] ?: 0).toString())
            .replace("{{spotbugsHigh}}", (spotbugsMetrics["high"] ?: 0).toString())
            .replace("{{spotbugsMedium}}", (spotbugsMetrics["medium"] ?: 0).toString())
            .replace("{{spotbugsLow}}", (spotbugsMetrics["low"] ?: 0).toString())
            .replace("{{testsPassed}}", (testMetrics["passed"] ?: 0).toString())
            .replace("{{testsTotal}}", (testMetrics["total"] ?: 0).toString())
            .replace("{{testsFailed}}", (testMetrics["failed"] ?: 0).toString())
            .replace("{{testsSkipped}}", (testMetrics["skipped"] ?: 0).toString())
            // Color replacements
            .replace("{{coverageColor}}", computeCoverageColor(jacocoMetrics["linesCoverage"] ?: "0.0"))
            .replace("{{branchCoverageColor}}", computeCoverageColor(jacocoMetrics["branchesCoverage"] ?: "0.0"))
            .replace("{{instructionCoverageColor}}", computeCoverageColor(jacocoMetrics["instructionsCoverage"] ?: "0.0"))
            .replace("{{checkstyleViolationColor}}", computeViolationColor(checkstyleMetrics["total"] ?: 0))
            .replace("{{pmdViolationColor}}", computeViolationColor(pmdMetrics["total"] ?: 0))
            .replace("{{spotbugsViolationColor}}", computeViolationColor(spotbugsMetrics["total"] ?: 0))
            .replace("{{testFailureColor}}", computeViolationColor(testMetrics["failed"] ?: 0))

        File(siteDir, "index.html").writeText(html)
        logger.lifecycle("Generated index.html")
    }

    /**
     * Computes a color value based on code coverage percentage.
     *
     * Color scale:
     * - Red (#dc3545): < 50% coverage
     * - Orange (#fd7e14): 50-75% coverage
     * - Yellow (#ffc107): 75-90% coverage
     * - Green (#28a745): >= 90% coverage
     *
     * @param coveragePercentage The coverage percentage as a string (e.g., "85.5")
     * @return A hex color code
     */
    private fun computeCoverageColor(coveragePercentage: String): String {
        return try {
            val coverage = coveragePercentage.toDouble()
            when {
                coverage >= 90.0 -> "#28a745"  // Green
                coverage >= 75.0 -> "#ffc107"  // Yellow
                coverage >= 50.0 -> "#fd7e14"  // Orange
                else -> "#dc3545"              // Red
            }
        } catch (e: NumberFormatException) {
            "#dc3545"  // Red for invalid values
        }
    }

    /**
     * Computes a color value based on violation/bug count.
     *
     * Color scale:
     * - Green (#28a745): 0 violations
     * - Yellow (#ffc107): 1-10 violations
     * - Orange (#fd7e14): 11-50 violations
     * - Red (#dc3545): > 50 violations
     *
     * @param violationCount The number of violations or bugs
     * @return A hex color code
     */
    private fun computeViolationColor(violationCount: Int): String {
        return when {
            violationCount == 0 -> "#28a745"    // Green
            violationCount <= 10 -> "#ffc107"   // Yellow
            violationCount <= 50 -> "#fd7e14"   // Orange
            else -> "#dc3545"                   // Red
        }
    }

    /**
     * Generates an aggregated index.html for multi-module projects.
     *
     * Loads the aggregated site template and generates a simple directory listing
     * of all modules with links to their individual documentation sites.
     *
     * @param siteDir The output directory where index.html will be written
     * @param modules The list of module names (subdirectories)
     * @throws IllegalStateException if the aggregated HTML template resource is not found
     */
    private fun generateAggregatedIndexHtml(siteDir: File, modules: List<String>) {
        val template = javaClass.getResourceAsStream("/templates/aggregated-site-index.html.template")?.bufferedReader()?.readText()
            ?: throw IllegalStateException("Aggregated HTML template not found")
        
        val buildDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val javaVersion = System.getProperty("java.version")
        
        // Generate module list HTML
        val moduleListHtml = modules.sorted().joinToString("\n") { moduleName ->
            """
                <a href="./$moduleName/index.html" class="module-link">
                    <strong>$moduleName</strong>
                    <span>View comprehensive documentation and quality metrics</span>
                </a>
            """.trimIndent()
        }
        
        val html = template
            .replace("{{projectName}}", projectName.get())
            .replace("{{projectVersion}}", projectVersion.get())
            .replace("{{buildDate}}", buildDate)
            .replace("{{javaVersion}}", javaVersion)
            .replace("{{gradleVersion}}", gradleVersion.get())
            .replace("{{moduleList}}", moduleListHtml)
        
        File(siteDir, "index.html").writeText(html)
        logger.lifecycle("Generated aggregated index.html for ${modules.size} module(s)")
    }
}
