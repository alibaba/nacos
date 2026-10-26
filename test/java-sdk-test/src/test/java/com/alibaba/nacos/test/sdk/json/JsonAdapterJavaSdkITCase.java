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

package com.alibaba.nacos.test.sdk.json;

import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import com.alibaba.nacos.api.utils.json.NacosTypeReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Packaged Client SDK JSON checks with Jackson 2 core/databind removed from the test JVM.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: auto/explicit Jackson 3 selection and generic JSON round trips.</li>
 *     <li>Boundary: assert that Jackson 2 core/databind cannot be loaded.</li>
 *     <li>Error handling: malformed JSON produces the neutral SDK deserialization exception.</li>
 * </ul>
 * Config and Naming remote operations, validation, listeners and cleanup reuse their existing
 * standalone IT classes in the same profile. See JAVA_SDK_IT_SCENARIOS.md.
 *
 * @author nacos
 */
@EnabledIfSystemProperty(named = "nacos.test.json.jackson3-only", matches = "true")
public class JsonAdapterJavaSdkITCase {

    @BeforeAll
    static void verifyRuntimeClasspath() {
        ClassLoader loader = JsonAdapterJavaSdkITCase.class.getClassLoader();
        assertThrows(ClassNotFoundException.class,
                () -> loader.loadClass("com.fasterxml.jackson.core.JsonProcessingException"));
        assertThrows(ClassNotFoundException.class,
                () -> loader.loadClass("com.fasterxml.jackson.databind.ObjectMapper"));
        assertEquals("jackson3", JsonUtils.selectedAdapterName());
    }

    @Test
    void testGenericRoundTrip() {
        Map<String, List<Integer>> value = Collections.singletonMap("items",
                Collections.singletonList(15788));
        String json = JsonUtils.toJson(value);
        assertEquals("{\"items\":[15788]}", json);
        assertEquals(value, JsonUtils.toObj(json,
                new NacosTypeReference<Map<String, List<Integer>>>() { }));
    }

    @Test
    void testMalformedJsonHasSdkException() {
        assertThrows(NacosDeserializationException.class,
                () -> JsonUtils.toObj("{broken}", Map.class));
    }
}
