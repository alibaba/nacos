/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.bootstrap;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Forked Maven build helper. Never packaged into the application or Native runtime classpath.
 */
public final class NacosNativeBuild {
    
    private NacosNativeBuild() {
    }
    
    /**
     * Generate and compile all selected application contexts in a dedicated JVM.
     *
     * @param args native output directory, application classes and experimental Derby options
     * @throws Exception when generation, validation or compilation fails
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                "Expected native output, application classes and Derby options");
        }
        Path output = Path.of(args[0]);
        Path generated = output.resolve("aot");
        NacosApplicationAotProcessor.main(new String[] {generated.toString()});
        Properties manifest = new Properties();
        try (InputStream input = Files.newInputStream(generated.resolve("resources/")
            .resolve(NacosAotInitializer.INDEX))) {
            manifest.load(input);
        }
        boolean derby = Boolean.getBoolean("nacos.native.derby.experimental");
        validateCapabilities(manifest, derby);
        Files.writeString(output.resolve("options.args"),
            derby ? Files.readString(Path.of(args[2]))
                : "# No experimental runtime class loading.\n");
        writeClasspath(output.resolve("classpath.args"),
            System.getProperty("nacos.native.classpath"));
        Path classes = output.resolve("classes");
        copyTree(Path.of(args[1]), classes);
        assembleGeneratedArtifacts(generated, classes);
        compileGeneratedSources(generated, classes, System.getProperty("java.class.path"));
        try (OutputStream stream =
            Files.newOutputStream(output.resolve("aot-manifest.properties"))) {
            manifest.store(stream,
                "Native structural configuration; connection details remain external");
        }
        Properties build = new Properties();
        build.setProperty("nacos.native.classifier",
            classifier(System.getProperty("os.name"), System.getProperty("os.arch")));
        try (OutputStream stream = Files.newOutputStream(output.resolve("build.properties"))) {
            build.store(stream, "Native build platform");
        }
    }
    
    static void writeClasspath(Path file, String classpath) throws IOException {
        if (classpath == null || classpath.isBlank()) {
            throw new IllegalArgumentException("Missing Native runtime classpath");
        }
        String escaped = classpath.replace("\\", "\\\\").replace("\"", "\\\"");
        Files.writeString(file, "-cp\n\"" + escaped + "\"\n");
    }
    
    static void validateCapabilities(Properties manifest, boolean experimentalDerby) {
        if (!"CONSOLE".equals(manifest.getProperty("deployment"))
            && "derby".equals(manifest.getProperty("datasource")) && !experimentalDerby) {
            throw new IllegalArgumentException(
                "Embedded Derby Native requires a toolchain with experimental "
                    + "runtime class loading and -Dnacos.native.derby.experimental=true; "
                    + "or choose an external datasource. Ordinary JVM Derby is unaffected.");
        }
    }
    
    static String classifier(String operatingSystem, String architecture) {
        String name = operatingSystem.toLowerCase(Locale.ROOT);
        String platform = name.contains("mac") ? "macos" : name.contains("windows") ? "windows"
            : name.contains("linux") ? "linux" : name.replaceAll("[^a-z0-9]+", "-");
        String arch = "amd64".equals(architecture) ? "x86_64"
            : "arm64".equals(architecture) ? "aarch64" : architecture;
        return platform + "-" + arch;
    }
    
    static void assembleGeneratedArtifacts(Path generated, Path classes) throws IOException {
        copyTree(generated.resolve("resources"), classes);
        try (Stream<Path> children = Files.list(generated)) {
            for (Path phase : children.filter(Files::isDirectory).sorted().toList()) {
                copyTree(phase.resolve("resources"), classes);
                copyTree(phase.resolve("classes"), classes);
            }
        }
    }
    
    static void copyTree(Path source, Path destination) throws IOException {
        if (!Files.isDirectory(source)) {
            return;
        }
        try (Stream<Path> files = Files.walk(source)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                Path target = destination.resolve(source.relativize(file));
                Files.createDirectories(target.getParent());
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
    
    static void compileGeneratedSources(Path generated, Path classes, String classpath)
        throws IOException {
        List<Path> sources;
        try (Stream<Path> files = Files.walk(generated)) {
            sources = files.filter(file -> file.toString().endsWith(".java")).sorted().toList();
        }
        if (sources.isEmpty()) {
            throw new IllegalStateException("No generated AOT sources in " + generated);
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException(
                "A full JDK is required to compile generated AOT sources");
        }
        Files.createDirectories(classes);
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, null, null)) {
            List<String> options = new ArrayList<>(List.of("--release", "17", "-parameters",
                "-classpath", classes + java.io.File.pathSeparator + classpath,
                "-d", classes.toString()));
            boolean compiled = compiler.getTask(null, manager, null, options, null,
                manager.getJavaFileObjectsFromPaths(sources)).call();
            if (!compiled) {
                throw new IllegalStateException("Generated AOT source compilation failed");
            }
        }
    }
}
