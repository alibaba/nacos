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

package com.alibaba.nacos.ai.service.mcp.storage;

import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McpJsonValidationSupportTest {
    
    private static final String CONTRACT_NAME = "test contract";
    
    @Test
    void testParseWrapsJacksonDeserializationFailure() {
        try (MockedStatic<JacksonUtils> jacksonMock = Mockito.mockStatic(JacksonUtils.class)) {
            jacksonMock.when(() -> JacksonUtils.toObj("{}", Object.class))
                .thenThrow(new NacosDeserializationException());
            assertThrows(IllegalArgumentException.class,
                () -> McpJsonValidationSupport.parseSingleValue("{}", CONTRACT_NAME));
        }
    }
    
    @Test
    void testRejectUnknownNonStringField() {
        Map<Object, Object> object = new LinkedHashMap<>();
        object.put(1, "value");
        assertThrows(IllegalArgumentException.class,
            () -> McpJsonValidationSupport.rejectUnknownFields(object,
                Collections.singleton("known"), CONTRACT_NAME));
    }
    
    @Test
    void testRequireIntegerSupportsIntegralTypes() {
        Map<String, Object> object = new LinkedHashMap<>();
        for (Number value : new Number[] {(byte) 1, (short) 2, 3, 4L}) {
            object.put("value", value);
            assertEquals(value.intValue(), McpJsonValidationSupport.requireInteger(object,
                "value", CONTRACT_NAME));
        }
        object.put("value", 1.0D);
        assertThrows(IllegalArgumentException.class,
            () -> McpJsonValidationSupport.requireInteger(object, "value", CONTRACT_NAME));
    }
    
    @Test
    void testRequireIntegerRejectsOutOfRangeLong() {
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("value", (long) Integer.MAX_VALUE + 1L);
        assertThrows(IllegalArgumentException.class,
            () -> McpJsonValidationSupport.requireInteger(object, "value", CONTRACT_NAME));
        object.put("value", (long) Integer.MIN_VALUE - 1L);
        assertThrows(IllegalArgumentException.class,
            () -> McpJsonValidationSupport.requireInteger(object, "value", CONTRACT_NAME));
    }
    
    @Test
    void testStrictParserRejectsWhitespaceOnlyInput() {
        assertThrows(IllegalArgumentException.class,
            () -> McpJsonValidationSupport.parseSingleValue("   ", CONTRACT_NAME));
    }
}
