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
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeHint;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.nativex.FileNativeConfigurationWriter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NacosApplicationAotProcessorTest {
    
    private static final List<String> INITIALIZERS = List.of(
        "example.App__CoreApplicationContextInitializer",
        "example.Web__WebApplicationContextInitializer",
        "example.Console__ConsoleApplicationContextInitializer",
        "example.Registry__AiRegistryApplicationContextInitializer");
    
    @TempDir
    Path temporary;
    
    @Test
    void shouldRegisterEveryGeneratedInitializerWithoutCondition() {
        RuntimeHints hints = new RuntimeHints();
        Properties index = index();
        index.setProperty("datasource", "mysql");
        index.setProperty("format", "2");
        NacosApplicationAotProcessor.registerInitializers(hints, index);
        for (String initializer : INITIALIZERS) {
            TypeHint hint = hints.reflection().getTypeHint(TypeReference.of(initializer));
            assertNotNull(hint, initializer);
            assertNull(hint.getReachableType(), initializer);
            assertTrue(
                hint.getMemberCategories().contains(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));
        }
        assertEquals(INITIALIZERS.size(), hints.reflection().typeHints().count());
    }
    
    @Test
    void shouldSkipPhasesThatWereNotGenerated() {
        RuntimeHints hints = new RuntimeHints();
        Properties index = new Properties();
        index.setProperty("core", INITIALIZERS.get(0));
        index.setProperty("web", "");
        NacosApplicationAotProcessor.registerInitializers(hints, index);
        assertEquals(1, hints.reflection().typeHints().count());
        assertNotNull(hints.reflection().getTypeHint(TypeReference.of(INITIALIZERS.get(0))));
    }
    
    @Test
    void shouldWriteInitializersWithoutTypeReachedCondition() throws Exception {
        RuntimeHints hints = new RuntimeHints();
        NacosApplicationAotProcessor.registerInitializers(hints, index());
        new FileNativeConfigurationWriter(temporary, "com.alibaba.nacos", "nacos-bootstrap-aot")
            .write(hints);
        Path metadata = temporary.resolve("META-INF/native-image/com.alibaba.nacos/"
            + "nacos-bootstrap-aot/reachability-metadata.json");
        String json = Files.readString(metadata);
        for (String initializer : INITIALIZERS) {
            assertTrue(json.contains(initializer), initializer);
        }
        assertFalse(json.contains("typeReached"));
    }
    
    @Test
    void shouldRegisterEveryGeneratedClassWithAllMemberCategories() throws Exception {
        Path generated = temporary.resolve("aot");
        String proxy = "example/service/Dump$$SpringCGLIB$$0";
        String fastClass = "example/service/Dump$$SpringCGLIB$$FastClass$$1";
        for (String name : List.of("core/classes/" + proxy, "web/classes/" + fastClass)) {
            Path file = generated.resolve(name + ".class");
            Files.createDirectories(file.getParent());
            Files.write(file, new byte[] {1});
        }
        Files.createDirectories(generated.resolve("resources/META-INF"));
        Files.writeString(generated.resolve("core/classes/readme.txt"), "not a class");
        Files.writeString(generated.resolve("core/sources.list"), "ignored");
        RuntimeHints hints = new RuntimeHints();
        NacosApplicationAotProcessor.registerGeneratedClasses(hints, generated);
        assertEquals(2, hints.reflection().typeHints().count());
        for (String name : List.of(proxy, fastClass)) {
            TypeHint hint = hints.reflection().getTypeHint(
                TypeReference.of(name.replace('/', '.')));
            assertNotNull(hint, name);
            assertNull(hint.getReachableType());
            assertTrue(hint.getMemberCategories().containsAll(List.of(
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.ACCESS_DECLARED_FIELDS, MemberCategory.UNSAFE_ALLOCATED)), name);
        }
        new FileNativeConfigurationWriter(temporary.resolve("written"), "com.alibaba.nacos", "aot")
            .write(hints);
        String json = Files.readString(temporary.resolve("written/META-INF/native-image/"
            + "com.alibaba.nacos/aot/reachability-metadata.json"));
        assertTrue(json.contains("\"example.service.Dump$$SpringCGLIB$$0\""), json);
        assertTrue(json.contains("\"unsafeAllocated\": true"), json);
        assertTrue(json.contains("\"allDeclaredFields\": true"), json);
    }
    
    @Test
    void shouldIgnoreMissingGeneratedClassDirectories() throws Exception {
        RuntimeHints hints = new RuntimeHints();
        NacosApplicationAotProcessor.registerGeneratedClasses(hints, temporary.resolve("absent"));
        Files.createDirectories(temporary.resolve("empty/core"));
        NacosApplicationAotProcessor.registerGeneratedClasses(hints, temporary.resolve("empty"));
        assertEquals(0, hints.reflection().typeHints().count());
    }
    
    private Properties index() {
        Properties index = new Properties();
        index.setProperty("core", INITIALIZERS.get(0));
        index.setProperty("web", INITIALIZERS.get(1));
        index.setProperty("console", INITIALIZERS.get(2));
        index.setProperty("ai-registry", INITIALIZERS.get(3));
        return index;
    }
}
