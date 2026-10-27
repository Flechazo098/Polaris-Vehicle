package com.flechazo.polaris.gradle

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

import javax.inject.Inject
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.jar.Manifest
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Expands nested compile APIs and merges the primary mod's development runtime. */
@CacheableTask
abstract class ExtractJarInJarCompileClasspath extends DefaultTask {
    @Classpath
    abstract ConfigurableFileCollection getContainer()

    /** Empty means every jar listed by META-INF/jarjar/metadata.json. */
    @Input
    abstract SetProperty<String> getIncludedIdentifiers()

    @OutputDirectory
    abstract DirectoryProperty getCompileDirectory()

    /** Main nested mod whose classes must share one dev module with the outer bootstrap jar. */
    @Input
    abstract Property<String> getRuntimePrimaryIdentifier()

    @OutputDirectory
    abstract DirectoryProperty getRuntimeDirectory()

    @Inject
    abstract FileSystemOperations getFileSystemOperations()

    @TaskAction
    void extractNestedJars() {
        Set<File> containers = container.files
        if (containers.size() != 1) {
            throw new GradleException("Expected one Jar-in-Jar container, got ${containers.size()}: ${containers}")
        }

        File outerJar = containers.first()
        File compileDestination = compileDirectory.get().asFile
        File runtimeDestination = runtimeDirectory.get().asFile
        fileSystemOperations.delete { delete(compileDestination, runtimeDestination) }
        Files.createDirectories(compileDestination.toPath())
        Files.createDirectories(runtimeDestination.toPath())

        Set<String> requested = includedIdentifiers.get()
        Set<String> writtenNames = new HashSet<>()
        Map<String, File> extractedByIdentifier = new LinkedHashMap<>()
        int extracted = 0
        new ZipFile(outerJar).withCloseable { zip ->
            def metadataEntry = zip.getEntry('META-INF/jarjar/metadata.json')
            if (metadataEntry == null) {
                throw new GradleException("${outerJar.name} has no META-INF/jarjar/metadata.json")
            }
            def metadata
            zip.getInputStream(metadataEntry).withCloseable { input ->
                metadata = new JsonSlurper().parse(input)
            }
            metadata.jars.each { nested ->
                String identifier = "${nested.identifier.group}:${nested.identifier.artifact}"
                if (!requested.isEmpty() && !requested.contains(identifier)) {
                    return
                }
                String nestedPath = nested.path as String
                if (!nestedPath.startsWith('META-INF/jarjar/')
                        || nestedPath.contains('\\')
                        || nestedPath.split('/').any { it == '..' || it.isEmpty() }) {
                    throw new GradleException("Unsafe Jar-in-Jar path in ${outerJar.name}: ${nestedPath}")
                }
                def nestedEntry = zip.getEntry(nestedPath)
                if (nestedEntry == null || nestedEntry.directory) {
                    throw new GradleException("Jar-in-Jar metadata points to a missing file: ${nestedPath}")
                }
                String filename = nestedPath.substring(nestedPath.lastIndexOf('/') + 1)
                if (!writtenNames.add(filename)) {
                    throw new GradleException("Two Jar-in-Jar entries have the same filename: ${filename}")
                }
                File output = new File(compileDestination, filename)
                zip.getInputStream(nestedEntry).withCloseable { input ->
                    Files.copy(input, output.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                extractedByIdentifier.put(identifier, output)
                extracted++
            }
        }
        if (extracted == 0) {
            throw new GradleException("No matching Jar-in-Jar entries found in ${outerJar.name}; requested ${requested}")
        }

        String primaryIdentifier = runtimePrimaryIdentifier.get()
        File primary = extractedByIdentifier.get(primaryIdentifier)
        if (primary == null) {
            throw new GradleException("Runtime primary ${primaryIdentifier} is not present in ${outerJar.name}; found ${extractedByIdentifier.keySet()}")
        }

        // Keep shared FRAPI dependencies nested so FML can deduplicate them against
        // Iris and other mods by Jar-in-Jar coordinates. Standalone extracted jars
        // otherwise enter MDG's legacy module layer a second time.
        mergeDevelopmentRuntime(
                outerJar,
                primary,
                primaryIdentifier,
                extractedByIdentifier,
                new File(runtimeDestination, "${outerJar.name.substring(0, outerJar.name.length() - 4)}-dev-merged.jar")
        )
    }

    private static void mergeDevelopmentRuntime(
            File outerJar,
            File primaryJar,
            String primaryIdentifier,
            Map<String, File> extractedByIdentifier,
            File output
    ) {
        Set<String> written = new HashSet<>()
        new ZipOutputStream(Files.newOutputStream(output.toPath())).withCloseable { destination ->
            // The outer jar is an early-bootstrap LIBRARY, but the merged dev
            // artifact is the nested MOD. Keeping the outer manifest silently
            // hides Sodium from FML even though its graphics probe still runs.
            new ZipFile(primaryJar).withCloseable { primary ->
                def manifestEntry = primary.getEntry('META-INF/MANIFEST.MF')
                def manifest = manifestEntry != null
                        ? primary.getInputStream(manifestEntry).withCloseable { new Manifest(it) }
                        : new Manifest()
                manifest.mainAttributes.putValue('Manifest-Version', '1.0')
                manifest.mainAttributes.putValue('FMLModType', 'MOD')
                ZipEntry entry = new ZipEntry('META-INF/MANIFEST.MF')
                entry.time = 0L
                destination.putNextEntry(entry)
                manifest.write(destination)
                destination.closeEntry()
                written.add(entry.name)
            }
            copyZipEntries(outerJar, destination, written, true)
            copyZipEntries(primaryJar, destination, written, false)
            new ZipFile(outerJar).withCloseable { zip ->
                def metadata
                zip.getInputStream(zip.getEntry('META-INF/jarjar/metadata.json')).withCloseable { input ->
                    metadata = new JsonSlurper().parse(input)
                }
                metadata.jars = metadata.jars.findAll { nested ->
                    String identifier = "${nested.identifier.group}:${nested.identifier.artifact}"
                    identifier != primaryIdentifier && extractedByIdentifier.containsKey(identifier)
                }
                if (!metadata.jars.isEmpty()) {
                    ZipEntry metadataOutput = new ZipEntry('META-INF/jarjar/metadata.json')
                    metadataOutput.time = 0L
                    destination.putNextEntry(metadataOutput)
                    destination.write(JsonOutput.toJson(metadata).getBytes(StandardCharsets.UTF_8))
                    destination.closeEntry()
                    metadata.jars.each { nested ->
                        ZipEntry nestedOutput = new ZipEntry(nested.path as String)
                        nestedOutput.time = 0L
                        destination.putNextEntry(nestedOutput)
                        String identifier = "${nested.identifier.group}:${nested.identifier.artifact}"
                        Files.copy(extractedByIdentifier.get(identifier).toPath(), destination)
                        destination.closeEntry()
                    }
                }
            }
        }
    }

    private static void copyZipEntries(
            File source,
            ZipOutputStream destination,
            Set<String> written,
            boolean outer
    ) {
        new ZipFile(source).withCloseable { zip ->
            zip.entries().toList().sort { it.name }.each { entry ->
                String name = entry.name
                if (entry.directory || !safeArchiveEntry(name)
                        || (outer && excludedOuterRuntimeEntry(name))
                        || !written.add(name)) {
                    return
                }
                ZipEntry outputEntry = new ZipEntry(name)
                outputEntry.time = 0L
                destination.putNextEntry(outputEntry)
                zip.getInputStream(entry).withCloseable { input -> input.transferTo(destination) }
                destination.closeEntry()
            }
        }
    }

    private static boolean safeArchiveEntry(String name) {
        return !name.startsWith('/') && !name.contains('\\')
                && !name.split('/').any { it == '..' || it.isEmpty() }
    }

    private static boolean excludedOuterRuntimeEntry(String name) {
        String upper = name.toUpperCase(Locale.ROOT)
        return name.startsWith('META-INF/jarjar/')
                || name == 'META-INF/services/net.neoforged.neoforgespi.locating.IModFileCandidateLocator'
                || upper.endsWith('.SF') || upper.endsWith('.RSA')
                || upper.endsWith('.DSA') || upper.endsWith('.EC')
    }
}
