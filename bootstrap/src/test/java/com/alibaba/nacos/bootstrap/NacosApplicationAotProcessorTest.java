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
    
    private Properties index() {
        Properties index = new Properties();
        index.setProperty("core", INITIALIZERS.get(0));
        index.setProperty("web", INITIALIZERS.get(1));
        index.setProperty("console", INITIALIZERS.get(2));
        index.setProperty("ai-registry", INITIALIZERS.get(3));
        return index;
    }
}
