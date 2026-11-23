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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import javax.tools.ToolProvider;
import java.io.InputStream;

import java.net.URLClassLoader;
import java.io.File;
import java.util.concurrent.TimeUnit;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

class NacosNativeBuildTest {
    
    @TempDir
    Path temporary;
    
    @Test
    void shouldCompileAndLoadGeneratedSourcesWithCopiedResources() throws Exception {
        Path generated = temporary.resolve("aot");
        write(generated, "resources/META-INF/nacos/aot/initializers.properties", "format=2");
        write(generated, "core/resources/core.txt", "core resource");
        write(generated, "web/resources/web.txt", "web resource");
        write(generated, "console/resources/console.txt", "console resource");
        write(generated, "aiRegistry/resources/registry.txt", "registry resource");
        write(generated, "console/classes/proxy.bin", "direct bytecode");
        write(generated, "core/sources/example/Core.java",
            "package example; public class Core { public static String value() { return \"core\"; } }");
        write(generated, "web/sources/example/Web.java",
            "package example; public class Web { public static String value() { return Core.value(); } }");
        Path output = temporary.resolve("compiled classes");
        NacosNativeBuild.assembleGeneratedArtifacts(generated, output);
        NacosNativeBuild.compileGeneratedSources(generated, output, "");
        assertEquals("format=2", Files.readString(output.resolve(NacosAotInitializer.INDEX)));
        assertEquals("direct bytecode", Files.readString(output.resolve("proxy.bin")));
        for (String resource : new String[] {"core.txt", "web.txt", "console.txt",
            "registry.txt"}) {
            assertTrue(Files.isRegularFile(output.resolve(resource)));
        }
        assertFalse(Files.exists(output.resolve("sources")));
        try (URLClassLoader loader =
            new URLClassLoader(new java.net.URL[] {output.toUri().toURL()}, null)) {
            assertEquals("core", loader.loadClass("example.Web").getMethod("value").invoke(null));
        }
    }
    
    @Test
    void shouldSupportConsoleOnlyArtifacts() throws Exception {
        Path generated = temporary.resolve("aot");
        write(generated, "console/resources/console.txt", "console");
        Path output = temporary.resolve("classes");
        NacosNativeBuild.assembleGeneratedArtifacts(generated, output);
        assertEquals("console", Files.readString(output.resolve("console.txt")));
        assertFalse(Files.exists(output.resolve("core")));
    }
    
    @Test
    void shouldFailWhenGeneratedSourcesAreMissingOrInvalid() throws Exception {
        Path generated = Files.createDirectory(temporary.resolve("aot"));
        Path output = temporary.resolve("classes");
        assertThrows(IllegalStateException.class,
            () -> NacosNativeBuild.compileGeneratedSources(generated, output, ""));
        write(generated, "core/sources/Invalid.java", "invalid Java source");
        assertThrows(IllegalStateException.class,
            () -> NacosNativeBuild.compileGeneratedSources(generated, output, ""));
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPrepareCompleteBuildOutput(boolean experimentalDerby) throws Exception {
        Path output = temporary.resolve("native output");
        Path generated = output.resolve("aot");
        String datasource = experimentalDerby ? "derby" : "mysql";
        write(generated, "resources/" + NacosAotInitializer.INDEX,
            "deployment=SERVER\ndatasource=" + datasource + "\ncore=example.Core\n");
        write(generated, "core/sources/example/Core.java", "package example; public class Core {}");
        write(generated, "core/classes/proxy.bin", "generated bytecode");
        Path application = temporary.resolve("application classes");
        write(application, "application.txt", "application resource");
        Path derbyOptions = temporary.resolve("derby.args");
        String experimentalOptions = "-H:+RuntimeClassLoading\n";
        Files.writeString(derbyOptions, experimentalOptions);
        String previousClasspath = System.getProperty("nacos.native.classpath");
        String previousExperimental = System.getProperty("nacos.native.derby.experimental");
        try (MockedStatic<NacosApplicationAotProcessor> processor =
            mockStatic(NacosApplicationAotProcessor.class)) {
            System.setProperty("nacos.native.classpath", application.toString());
            System.setProperty("nacos.native.derby.experimental",
                Boolean.toString(experimentalDerby));
            NacosNativeBuild.main(new String[] {output.toString(), application.toString(),
                derbyOptions.toString()});
            processor.verify(() -> NacosApplicationAotProcessor.main(
                new String[] {generated.toString()}));
            Path classes = output.resolve("classes");
            assertTrue(Files.isRegularFile(classes.resolve("example/Core.class")));
            assertEquals("application resource",
                Files.readString(classes.resolve("application.txt")));
            assertEquals("generated bytecode", Files.readString(classes.resolve("proxy.bin")));
            Properties manifest = readProperties(output.resolve("aot-manifest.properties"));
            assertEquals(datasource, manifest.getProperty("datasource"));
            assertEquals(readProperties(classes.resolve(NacosAotInitializer.INDEX)), manifest);
            assertEquals(NacosNativeBuild.classifier(System.getProperty("os.name"),
                System.getProperty("os.arch")),
                readProperties(output.resolve("build.properties"))
                    .getProperty("nacos.native.classifier"));
            assertEquals("-cp\n\"" + application.toString().replace("\\", "\\\\")
                + "\"\n", Files.readString(output.resolve("classpath.args")));
            String options = Files.readString(output.resolve("options.args"));
            if (experimentalDerby) {
                assertEquals(experimentalOptions, options);
            } else {
                assertFalse(options.contains("RuntimeClassLoading"));
            }
        } finally {
            restoreProperty("nacos.native.classpath", previousClasspath);
            restoreProperty("nacos.native.derby.experimental", previousExperimental);
        }
    }
    
    @Test
    void shouldRejectRuntimeWithoutJavaCompiler() throws Exception {
        Path generated = temporary.resolve("aot");
        write(generated, "core/sources/example/Core.java", "package example; public class Core {}");
        try (MockedStatic<ToolProvider> provider = mockStatic(ToolProvider.class)) {
            IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> NacosNativeBuild.compileGeneratedSources(generated,
                    temporary.resolve("classes"), ""));
            assertTrue(error.getMessage().contains("A full JDK is required"));
            provider.verify(ToolProvider::getSystemJavaCompiler);
        }
    }
    
    @ParameterizedTest
    @CsvSource({"MERGED,derby,false,false", "SERVER,derby,false,false",
        "MERGED,derby,true,true", "CONSOLE,derby,false,true", "SERVER,mysql,false,true",
        "SERVER,postgresql,false,true", "SERVER,custom,false,true"})
    void shouldRequireExplicitExperimentalDerbyForServer(String deployment, String datasource,
        boolean experimental, boolean valid) {
        Properties manifest = new Properties();
        manifest.setProperty("deployment", deployment);
        manifest.setProperty("datasource", datasource);
        if (valid) {
            assertDoesNotThrow(() -> NacosNativeBuild.validateCapabilities(manifest, experimental));
        } else {
            assertThrows(IllegalArgumentException.class,
                () -> NacosNativeBuild.validateCapabilities(manifest, experimental));
        }
    }
    
    @ParameterizedTest
    @CsvSource({"Mac OS X,aarch64,macos-aarch64", "Linux,amd64,linux-x86_64",
        "Windows 11,amd64,windows-x86_64", "Linux,arm64,linux-aarch64",
        "Free BSD,sparcv9,free-bsd-sparcv9"})
    void shouldIdentifyBuildPlatform(String operatingSystem, String architecture, String expected) {
        assertEquals(expected, NacosNativeBuild.classifier(operatingSystem, architecture));
    }
    
    @Test
    void shouldQuoteRuntimeClasspathWithoutIncludingBuildTools() throws Exception {
        Path arguments = temporary.resolve("classpath.args");
        String classpath = "/build/classes:/plugins/custom plugin.jar";
        NacosNativeBuild.writeClasspath(arguments, classpath);
        assertEquals("-cp\n\"" + classpath + "\"\n", Files.readString(arguments));
        assertThrows(IllegalArgumentException.class,
            () -> NacosNativeBuild.writeClasspath(arguments, " "));
        assertThrows(IllegalArgumentException.class,
            () -> NacosNativeBuild.writeClasspath(arguments, null));
    }
    
    @Test
    void shouldLaunchJavaUsingGeneratedClasspathArgumentFile() throws Exception {
        Path generated = temporary.resolve("aot");
        write(generated, "core/sources/example/Main.java",
            "package example; public class Main { public static void main(String[] args) { "
                + "System.out.print(\"argument-file-ok\"); } }");
        Path classes = temporary.resolve("compiled classes");
        NacosNativeBuild.compileGeneratedSources(generated, classes, "");
        Path arguments = temporary.resolve("runtime args.txt");
        NacosNativeBuild.writeClasspath(arguments, classes.toString());
        String executable = File.separatorChar == '\\' ? "java.exe" : "java";
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin",
            executable).toString(), "@" + arguments, "example.Main")
            .redirectErrorStream(true).start();
        try {
            assertTrue(process.waitFor(15, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue());
            assertEquals("argument-file-ok", new String(process.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8));
        } finally {
            process.destroyForcibly();
        }
    }
    
    @Test
    void shouldRejectInvalidBuildArguments() {
        assertThrows(IllegalArgumentException.class, () -> NacosNativeBuild.main(new String[0]));
    }
    
    private Properties readProperties(Path path) throws Exception {
        Properties result = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            result.load(input);
        }
        return result;
    }
    
    private void restoreProperty(String key, String previous) {
        if (previous == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previous);
        }
    }
    
    private void write(Path root, String relative, String content) throws Exception {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
