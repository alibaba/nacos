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

package com.alibaba.nacos.core.distributed.raft.storage;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** Shared isolated-JVM launcher for real Raft tests without RocksDB JNI. */
public final class RaftProcessSupport {
    
    private RaftProcessSupport() {
    }
    
    /**
     * Starts a test main with real RPC and class-load diagnostics.
     * @param main entry point
     * @param log child output
     * @param args application arguments
     * @return running process owned by the caller
     * @throws IOException when launching fails
     */
    public static Process start(Class<?> main, Path log, String... args) throws IOException {
        String classpath = Arrays.stream(System.getProperty("surefire.test.class.path",
            System.getProperty("java.class.path")).split(File.pathSeparator))
            .filter(entry -> !entry.toLowerCase(Locale.ROOT).contains("rocksdb"))
            .collect(Collectors.joining(File.pathSeparator));
        assertFalse(classpath.toLowerCase(Locale.ROOT).contains("rocksdb"));
        List<String> arguments = ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
            .filter(argument -> argument.startsWith("--add-opens="))
            .map(RaftProcessSupport::quote)
            .collect(Collectors.toCollection(ArrayList::new));
        arguments.addAll(List.of("-Xlog:class+load=info", "-cp", quote(classpath), main.getName()));
        for (String arg : args) {
            arguments.add(quote(arg));
        }
        Path argumentFile = log.resolveSibling(log.getFileName() + ".args");
        Files.write(argumentFile, arguments);
        return new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
            .toString(), "@" + argumentFile).redirectErrorStream(true)
            .redirectOutput(log.toFile()).start();
    }
    
    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
    
    /**
     * Checks that the child never loaded the native backend.
     * @param log child output with class-load diagnostics
     * @throws IOException if diagnostics cannot be read
     */
    public static void assertNoRocksDb(Path log) throws IOException {
        String output = Files.readString(log);
        assertFalse(output.contains("org.rocksdb."), log.toString());
        assertFalse(output.contains("com.alipay.sofa.jraft.storage.impl.RocksDBLogStorage"),
            log.toString());
    }
}
