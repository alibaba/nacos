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
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Starts the database and the Native Server and keeps them running for the integration suites of
 * the other test modules, which run unchanged as Maven builds against it. The environment is
 * ready when {@code environment.properties} exists in the directory; it holds the credentials as
 * the system properties that the suites read. A developer starts it in a terminal, runs any test
 * against it, and stops it with Ctrl-C.
 *
 * <p>Where a script drives it, the file {@code finish} in the directory ends the run. The
 * environment then restarts the Server, which holds the data of the suites by now, and checks that
 * the data of {@link NativeRestartCheck} survives, writes {@code restart-check.txt}, and exits
 * with 1 if a step failed.
 *
 * <p>Usage: {@code NativeEnvironmentMain [postgresql|mysql] [directory]}.
 *
 * @author Nacos
 */
public final class NativeEnvironmentMain {

    private NativeEnvironmentMain() {
    }

    /**
     * Runs the environment.
     *
     * @param args the database and the directory, both optional
     * @throws InterruptedException if the thread is interrupted
     */
    public static void main(String[] args) throws InterruptedException {
        String platform = args.length > 0 ? args[0] : NativeTestSettings.database();
        Path directory = args.length > 1 ? Path.of(args[1]).toAbsolutePath()
                : NativeTestSettings.workDirectory();
        Path finish = directory.resolve("finish");
        try {
            Files.createDirectories(directory);
            Files.deleteIfExists(finish);
            Files.deleteIfExists(directory.resolve("environment.properties"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        System.setProperty(NativeTestSettings.WORK, directory.toString());
        int status = 0;
        try (NativeEnvironment environment = NativeEnvironment.create("environment", platform,
                Map.of()).start()) {
            environment.exportSystemProperties();
            // The suites address the Server through these properties instead of the defaults.
            System.setProperty("nacos.port", String.valueOf(environment.server().apiPort()));
            System.setProperty("nacos.console.port",
                    String.valueOf(environment.server().consolePort()));
            writeProperties(directory.resolve("environment.properties"));
            System.out.println("The native test environment is ready: "
                    + directory.resolve("environment.properties"));
            while (!Files.exists(finish)) {
                Thread.sleep(500);
            }
            List<String> report = NativeRestartCheck.run(environment);
            Files.write(directory.resolve("restart-check.txt"), report, StandardCharsets.UTF_8);
            if (report.stream().anyMatch(line -> line.startsWith("FAIL"))) {
                status = 1;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        System.exit(status);
    }

    private static void writeProperties(Path file) throws IOException {
        Map<String, String> sorted = new TreeMap<>();
        Properties all = System.getProperties();
        for (String name : all.stringPropertyNames()) {
            if (name.startsWith("nacos.test.auth.") || name.startsWith("nacos.agent.it.")
                    || name.equals("nacos.port") || name.equals("nacos.console.port")) {
                sorted.put(name, all.getProperty(name));
            }
        }
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> each : sorted.entrySet()) {
                writer.write(each.getKey() + '=' + each.getValue() + '\n');
            }
        }
        // The file appears complete or not at all, for the script that waits for it.
        Files.move(temporary, file, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }
}
