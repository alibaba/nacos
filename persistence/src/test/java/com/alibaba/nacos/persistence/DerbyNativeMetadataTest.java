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

package com.alibaba.nacos.persistence;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.derby.iapi.services.io.RegisteredFormatIds;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DerbyNativeMetadataTest {
    
    @Test
    void shouldRegisterStorageFormatConstructorsForRecovery() throws Exception {
        Set<String> expected = new TreeSet<>();
        for (int id = 0; id < RegisteredFormatIds.countTwoByteIDs(); id++) {
            String name = RegisteredFormatIds.classNameForTwoByteID(id);
            if (name != null && name.startsWith("org.apache.derby.impl.store.")) {
                expected.add(name);
            }
        }
        assertTrue(expected.contains("org.apache.derby.impl.store.raw.log.CheckpointOperation"));
        Set<String> registered = new TreeSet<>();
        try (InputStream stream = getClass().getResourceAsStream(
            "/META-INF/native-image/com.alibaba.nacos/nacos-persistence/reflect-config.json")) {
            assertNotNull(stream);
            JsonNode metadata = JacksonUtils.toObj(
                new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            assertTrue(metadata.isArray());
            for (JsonNode entry : metadata) {
                String name = entry.get("name").asText();
                assertTrue(registered.add(name), "Duplicate registration: " + name);
                assertNotNull(
                    Class.forName(name, false, getClass().getClassLoader()).getConstructor());
                assertEquals(1, entry.get("methods").size());
                JsonNode constructor = entry.get("methods").get(0);
                assertEquals("<init>", constructor.get("name").asText());
                assertTrue(constructor.get("parameterTypes").isArray());
                assertEquals(0, constructor.get("parameterTypes").size());
            }
        }
        assertEquals(expected, registered,
            "Keep Native recovery metadata aligned with Derby storage format IDs");
    }
}
