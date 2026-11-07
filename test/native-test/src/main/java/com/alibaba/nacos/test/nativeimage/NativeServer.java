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
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * One Server, a process of the host that runs from the unpacked distribution. The Native
 * executable is started by {@code bin/startup-native.sh}, which is part of what these tests check,
 * and the ordinary Server by {@code bin/startup.sh}. The process is found again by the home
 * directory on its command line, so the tests need no pid file.
 *
 * @author Nacos
 */
public final class NativeServer implements AutoCloseable {

    /**
     * The port of the API, whose context path is {@code /nacos}.
     */
    public static final int API_PORT = 8848;

    /**
     * The port of the console.
     */
    public static final int CONSOLE_PORT = 8080;

    private static final Duration LIVE_TIMEOUT = Duration.ofSeconds(180);

    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(60);

    private final String name;

    private final Path home;

    private final Map<String, String> properties = new LinkedHashMap<>();

    private final Map<String, String> files = new LinkedHashMap<>();

    private final List<Path> plugins = new ArrayList<>();

    private final String options;

    private final boolean jvm;

    private final boolean standalone;

    private final int apiPort;

    private final int consolePort;

    private boolean configured;

    private Thread shutdownHook;

    private NativeServer(Builder builder, Path home) {
        this.name = builder.name;
        this.home = home;
        this.properties.putAll(builder.properties);
        this.files.putAll(builder.files);
        this.plugins.addAll(builder.plugins);
        this.options = NativeTestSettings.options();
        this.jvm = NativeTestSettings.jvmMode();
        this.standalone = builder.standalone;
        this.apiPort = builder.apiPort;
        this.consolePort = builder.consolePort;
        this.properties.put("nacos.server.main.port", String.valueOf(apiPort));
        this.properties.put("nacos.console.port", String.valueOf(consolePort));
    }

    /**
     * Unpacks the distribution under the work directory and prepares a standalone Server on the
     * default ports.
     *
     * @param name       the name of the Server, a directory name below the work directory
     * @param properties properties for {@code conf/application.properties}
     * @return a Server that is not started yet
     */
    public static NativeServer prepare(String name, Map<String, String> properties) {
        return builder(name).properties(properties).prepare();
    }

    /**
     * Starts the description of a Server.
     *
     * @param name the name of the Server, a directory name below the work directory
     * @return a builder
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * The description of a Server.
     */
    public static final class Builder {

        private final String name;

        private final Map<String, String> properties = new LinkedHashMap<>();

        private final Map<String, String> files = new LinkedHashMap<>();

        private final List<Path> plugins = new ArrayList<>();

        private boolean standalone = true;

        private int apiPort = API_PORT;

        private int consolePort = CONSOLE_PORT;

        private Builder(String name) {
            this.name = name;
        }

        /**
         * Adds properties of {@code conf/application.properties}.
         *
         * @param more the properties
         * @return this builder
         */
        public Builder properties(Map<String, String> more) {
            properties.putAll(more);
            return this;
        }

        /**
         * Adds a property of {@code conf/application.properties}.
         *
         * @param key   the key
         * @param value the value
         * @return this builder
         */
        public Builder property(String key, String value) {
            properties.put(key, value);
            return this;
        }

        /**
         * Writes a file below the home before the first start.
         *
         * @param path    the path relative to the home, for example {@code conf/cluster.conf}
         * @param content the content
         * @return this builder
         */
        public Builder file(String path, String content) {
            files.put(path, content);
            return this;
        }

        /**
         * Adds a plugin jar. The ordinary Server loads it from {@code plugins}. A Native
         * executable has the plugins of its build, so this does nothing for it.
         *
         * @param jar the jar
         * @return this builder
         */
        public Builder plugin(Path jar) {
            plugins.add(jar);
            return this;
        }

        /**
         * Sets the ports of the API and the console. The ports of gRPC and Raft follow the API
         * port by fixed offsets.
         *
         * @param api     the API port
         * @param console the console port
         * @return this builder
         */
        public Builder ports(int api, int console) {
            this.apiPort = api;
            this.consolePort = console;
            return this;
        }

        /**
         * Selects cluster mode for the ordinary Server. A Native executable has the mode it was
         * built for.
         *
         * @param value false for cluster mode
         * @return this builder
         */
        public Builder standalone(boolean value) {
            this.standalone = value;
            return this;
        }

        /**
         * Unpacks the distribution under the work directory.
         *
         * @return a Server that is not started yet
         */
        public NativeServer prepare() {
            Path directory = NativeTestSettings.workDirectory().resolve(name);
            deleteRecursively(directory);
            try {
                Files.createDirectories(directory);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            run(directory, List.of("tar", "-xzf", NativeTestSettings.distribution().toString(),
                    "-C", directory.toString()), Duration.ofMinutes(2));
            return new NativeServer(this, directory.resolve("nacos"));
        }
    }

    /**
     * The home of the Server.
     *
     * @return the unpacked {@code nacos} directory
     */
    public Path home() {
        return home;
    }

    /**
     * The base URL of the API.
     *
     * @return {@code http://127.0.0.1:8848} unless the ports were set
     */
    public String apiUrl() {
        return "http://127.0.0.1:" + apiPort;
    }

    /**
     * The base URL of the console.
     *
     * @return {@code http://127.0.0.1:8080} unless the ports were set
     */
    public String consoleUrl() {
        return "http://127.0.0.1:" + consolePort;
    }

    /**
     * The port of the API.
     *
     * @return the API port the Server was prepared with
     */
    public int apiPort() {
        return apiPort;
    }

    /**
     * The port of the console.
     *
     * @return the console port the Server was prepared with
     */
    public int consolePort() {
        return consolePort;
    }

    /**
     * A port that nothing listens on right now, for a Server or a test to bind.
     *
     * @param excluded ports to avoid, for example the API port when a console port is needed
     * @return a free port
     */
    static int freePort(int... excluded) {
        for (;;) {
            try (ServerSocket socket = new ServerSocket(0)) {
                int port = socket.getLocalPort();
                boolean taken = false;
                for (int each : excluded) {
                    if (each == port) {
                        taken = true;
                        break;
                    }
                }
                if (!taken) {
                    return port;
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /**
     * Starts the process and waits until the console answers its liveness check.
     */
    public void start() {
        assertPortsFree();
        try {
            if (!configured) {
                writeConfiguration();
                configured = true;
            }
            Files.createDirectories(home.resolve("logs"));
            List<String> command = !jvm ? List.of("bash", "bin/startup-native.sh")
                    : standalone ? List.of("bash", "bin/startup.sh", "-m", "standalone")
                    : List.of("bash", "bin/startup.sh");
            ProcessBuilder builder = new ProcessBuilder(command).directory(home.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(home.resolve("logs/launcher.out").toFile());
            if (jvm) {
                builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
                builder.environment().put("CUSTOM_NACOS_MEMORY", options);
                String jvmOptions = NativeTestSettings.jvmOptions();
                if (!jvmOptions.isEmpty()) {
                    builder.environment().put("JAVA_TOOL_OPTIONS",
                            jvmOptions.replace("{name}", name));
                }
            } else {
                // Without this property the Server logs to ${user.home}/nacos/logs.
                builder.environment().put("NACOS_NATIVE_OPTS",
                        options + " -Dnacos.logs.path=" + home.resolve("logs"));
            }
            Process launcher = builder.start();
            if (!launcher.waitFor(60, TimeUnit.SECONDS) || launcher.exitValue() != 0) {
                throw new IllegalStateException("The launcher of " + name + " failed:\n"
                        + read(home.resolve("logs/launcher.out")));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        shutdownHook = new Thread(this::stopQuietly, "stop-" + name);
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        awaitLive();
    }

    /**
     * Stops the process with SIGTERM, as an operator does, and waits for it to end.
     *
     * @return true if the process ended by itself, false if it had to be killed after 60 seconds
     */
    public boolean stop() {
        Optional<ProcessHandle> handle = process();
        if (handle.isEmpty()) {
            return true;
        }
        handle.get().destroy();
        boolean graceful = true;
        try {
            handle.get().onExit().get(STOP_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            handle.get().destroyForcibly();
            graceful = false;
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // The JVM is shutting down already.
            }
            shutdownHook = null;
        }
        return graceful;
    }

    /**
     * Stops the Server, keeps the log of the first run and starts it again on the same home.
     */
    public void restart() {
        stop();
        try {
            // The launcher truncates startup.log.
            Path log = startupLog();
            if (Files.isRegularFile(log)) {
                Files.copy(log, home.resolve("logs/startup-before-restart.log"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        start();
    }

    @Override
    public void close() {
        stopQuietly();
    }

    /**
     * The log that the launcher writes the output of the process to.
     *
     * @return {@code logs/startup.log} of the Native Server, {@code logs/start.out} of the JVM
     */
    public Path startupLog() {
        return home.resolve(jvm ? "logs/start.out" : "logs/startup.log");
    }

    /**
     * The text of all log files below {@code logs}.
     *
     * @return the concatenated logs
     */
    public String allLogs() {
        try (Stream<Path> files = Files.list(home.resolve("logs"))) {
            return files.filter(file -> file.getFileName().toString().endsWith(".log")
                    || file.getFileName().toString().endsWith(".out")).sorted()
                    .map(NativeServer::read).collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * How often the Raft groups saved a snapshot since the start.
     *
     * @return the number of log lines that report a saved snapshot
     */
    public long snapshotsSaved() {
        return allLogs().lines().filter(line -> line.contains("SnapshotOperation.SAVE cost time"))
                .count();
    }

    /**
     * Waits for a text in one of the logs.
     *
     * @param text    the text
     * @param timeout how long to wait
     * @return true if the text appeared
     */
    public boolean awaitLog(String text, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (allLogs().contains(text)) {
                return true;
            }
            sleep(1000);
        }
        return false;
    }

    private Optional<ProcessHandle> process() {
        String marker = "-Dnacos.home=" + home;
        return ProcessHandle.allProcesses().filter(handle -> handle.info().commandLine()
                .map(line -> line.contains(marker)).orElse(false)).findFirst();
    }

    private void stopQuietly() {
        try {
            stop();
        } catch (RuntimeException ignored) {
            // The process is gone or cannot be stopped; there is nothing more to do.
        }
    }

    private void awaitLive() {
        Duration liveTimeout = NativeTestSettings.scaled(LIVE_TIMEOUT);
        long deadline = System.nanoTime() + liveTimeout.toNanos();
        NativeHttp http = new NativeHttp();
        while (System.nanoTime() < deadline) {
            if (http.get(consoleUrl() + "/v3/console/health/liveness", null).status() == 200) {
                return;
            }
            if (process().isEmpty()) {
                throw new IllegalStateException(name + " ended while it started:\n"
                        + tail(startupLog(), 200));
            }
            sleep(200);
        }
        throw new IllegalStateException(name + " is not live after " + liveTimeout.toSeconds()
                + " s:\n" + tail(startupLog(), 200));
    }

    private void assertPortsFree() {
        // The ports of gRPC and Raft follow the API port by fixed offsets.
        for (int port : new int[] {apiPort, consolePort, apiPort + 1000, apiPort + 1001,
                apiPort - 1000}) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 200);
                throw new IllegalStateException("Port " + port + " is in use already, probably by "
                        + "another Nacos Server; stop it before the native tests run");
            } catch (IOException expected) {
                // Nothing listens, as it has to be.
            }
        }
    }

    /**
     * Sets the properties in {@code conf/application.properties}. A line that sets the key
     * already is replaced, because the launcher of the JVM Server reads the first one of them and
     * asks for it on a terminal if it is empty. The others are appended.
     */
    private void writeConfiguration() throws IOException {
        if (jvm) {
            Files.createDirectories(home.resolve("plugins"));
            for (Path jar : plugins) {
                Files.copy(jar, home.resolve("plugins").resolve(jar.getFileName()),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
        for (Map.Entry<String, String> each : files.entrySet()) {
            Path target = home.resolve(each.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, each.getValue(), StandardCharsets.UTF_8);
        }
        Path file = home.resolve("conf/application.properties");
        List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
        List<String> appended = new ArrayList<>();
        properties.forEach((key, value) -> {
            boolean replaced = false;
            for (int index = 0; index < lines.size(); index++) {
                if (lines.get(index).startsWith(key + '=')) {
                    lines.set(index, key + '=' + value);
                    replaced = true;
                }
            }
            if (!replaced) {
                appended.add(key + '=' + value);
            }
        });
        lines.add("");
        lines.add("# written by the native tests: " + name);
        lines.addAll(appended);
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private static void run(Path directory, List<String> command, Duration timeout) {
        try {
            Process process = new ProcessBuilder(command).directory(directory.toFile())
                    .redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            if (!process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IllegalStateException(String.join(" ", command) + " failed:\n" + output);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void deleteRecursively(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            List<Path> reversed = new ArrayList<>(paths.collect(Collectors.toList()));
            java.util.Collections.reverse(reversed);
            for (Path each : reversed) {
                Files.delete(each);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            return "(unreadable: " + e.getMessage() + ")";
        }
    }

    private static String tail(Path file, int lines) {
        List<String> all = read(file).lines().collect(Collectors.toList());
        return String.join("\n", all.subList(Math.max(0, all.size() - lines), all.size()));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
