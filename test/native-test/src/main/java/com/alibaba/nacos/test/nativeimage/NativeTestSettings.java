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

package com.alibaba.nacos.test.nativeimage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Settings of a native test run. All of them are system properties, which the
 * {@code native-integration-test} profile of this module sets from Maven properties.
 *
 * @author Nacos
 */
public final class NativeTestSettings {

    /**
     * The distribution archive of the Server; empty finds it in {@code distribution/target}.
     */
    public static final String DISTRIBUTION = "nacos.native.test.distribution";

    /**
     * {@code native} runs the Native executable, {@code jvm} the ordinary Server.
     */
    public static final String MODE = "nacos.native.test.mode";

    /**
     * Options of the Native executable, the value of {@code NACOS_NATIVE_OPTS}.
     */
    public static final String OPTIONS = "nacos.native.test.opts";

    /**
     * More options for the JVM of the ordinary Server (mode {@code jvm}), passed as {@code
     * JAVA_TOOL_OPTIONS}. The placeholder {@code name} in braces is replaced by the name of the
     * Server, so that several Servers can write to directories of their own. It is how the
     * tracing agent of GraalVM records what a feature reaches by reflection:
     * {@code -agentlib:native-image-agent=config-merge-dir=/some/directory}.
     */
    public static final String JVM_OPTIONS = "nacos.native.test.jvm.options";

    /**
     * A factor for every wait of the tests (default 1), for a slow machine or for a Server that runs
     * under the tracing agent of GraalVM, which starts several times slower.
     */
    public static final String TIMEOUT_SCALE = "nacos.native.test.timeout.scale";

    /**
     * The database of the tests: {@code postgresql} or {@code mysql}.
     */
    public static final String DATABASE = "nacos.native.test.database";

    /**
     * The directory that receives the homes and the logs of the Servers.
     */
    public static final String WORK = "nacos.native.test.work";

    /**
     * The prefix of system properties that are passed on to {@code conf/application.properties}
     * of every Server: {@code -Dnacos.native.test.property.nacos.ai.ard.enabled=true} sets
     * {@code nacos.ai.ard.enabled=true}. The structural choices of the Native build, such as the
     * switches of the AI registries, have to be repeated that way, because the executable
     * refuses to start with other values.
     */
    public static final String PROPERTY_PREFIX = "nacos.native.test.property.";

    private static final String LAUNCHER = "distribution/bin/startup-native.sh";

    private NativeTestSettings() {
    }

    /**
     * The properties that the run adds to the configuration of every Server.
     *
     * @return the properties, without the prefix
     */
    public static Map<String, String> serverProperties() {
        Map<String, String> properties = new LinkedHashMap<>();
        for (String name : System.getProperties().stringPropertyNames()) {
            if (name.startsWith(PROPERTY_PREFIX)) {
                properties.put(name.substring(PROPERTY_PREFIX.length()),
                        System.getProperty(name));
            }
        }
        return properties;
    }

    /**
     * Whether the ordinary JVM Server runs instead of the Native executable.
     *
     * @return true for the JVM Server
     */
    public static boolean jvmMode() {
        return "jvm".equalsIgnoreCase(System.getProperty(MODE, "native"));
    }

    /**
     * More options for the JVM of the ordinary Server.
     *
     * @return the value of {@link #JVM_OPTIONS}, empty if there is none
     */
    public static String jvmOptions() {
        return System.getProperty(JVM_OPTIONS, "").trim();
    }

    /**
     * A wait of the tests, multiplied by {@link #TIMEOUT_SCALE}.
     *
     * @param timeout the wait on a machine of the usual speed
     * @return the wait to use
     */
    public static Duration scaled(Duration timeout) {
        double scale = Double.parseDouble(System.getProperty(TIMEOUT_SCALE, "1"));
        return Duration.ofMillis(Math.round(timeout.toMillis() * scale));
    }

    /**
     * The options of the Native executable.
     *
     * @return the value of {@code NACOS_NATIVE_OPTS}
     */
    public static String options() {
        return System.getProperty(OPTIONS, "-Xmx1g");
    }

    /**
     * The database of the tests.
     *
     * @return {@code postgresql} unless the property says otherwise
     */
    public static String database() {
        return System.getProperty(DATABASE, "postgresql");
    }

    /**
     * The root of the repository, found from the working directory of the test.
     *
     * @return the directory that holds {@code distribution/bin/startup-native.sh}
     */
    public static Path repositoryRoot() {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            if (Files.isRegularFile(directory.resolve(LAUNCHER))) {
                return directory;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("No " + LAUNCHER + " above " + Path.of("").toAbsolutePath());
    }

    /**
     * The jar of the sample plugin of {@code test/native-test-plugin}.
     *
     * @return the jar that a build left in the {@code target} directory of that module
     */
    public static Path samplePlugin() {
        Path target = repositoryRoot().resolve("test/native-test-plugin/target");
        try (Stream<Path> files = Files.list(target)) {
            return files.filter(file -> {
                String name = file.getFileName().toString();
                return name.startsWith("native-test-plugin-") && name.endsWith(".jar")
                        && !name.endsWith("-sources.jar");
            }).findFirst().orElseThrow(() -> new IllegalStateException("No jar in " + target
                    + "; build the module test/native-test-plugin"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The directory that receives the homes and the logs of the Servers.
     *
     * @return the directory named by {@link #WORK}, by default {@code target/native-test} of this
     *         module
     */
    public static Path workDirectory() {
        String configured = System.getProperty(WORK, "");
        if (!configured.isBlank()) {
            return Path.of(configured).toAbsolutePath();
        }
        return repositoryRoot().resolve("test/native-test/target/native-test");
    }

    /**
     * The distribution archive of the Server under test.
     *
     * @return the archive named by {@link #DISTRIBUTION}, or the one that a build left in
     *         {@code distribution/target}
     */
    public static Path distribution() {
        String configured = System.getProperty(DISTRIBUTION, "");
        if (!configured.isBlank()) {
            return Path.of(configured).toAbsolutePath();
        }
        Path target = repositoryRoot().resolve("distribution/target");
        boolean jvm = jvmMode();
        try (Stream<Path> files = Files.list(target)) {
            List<Path> found = files.filter(file -> {
                String name = file.getFileName().toString();
                return name.startsWith("nacos-server-") && name.endsWith(".tar.gz")
                        && name.contains("-native-") != jvm;
            }).sorted().collect(Collectors.toList());
            if (found.isEmpty()) {
                throw new IllegalStateException("No " + (jvm ? "JVM" : "Native")
                        + " distribution archive in " + target + "; build it or set "
                        + DISTRIBUTION);
            }
            return found.get(0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
