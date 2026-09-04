/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GetConfigRequestTest {

    @Test
    @DisplayName("test default constructor")
    void testDefaultConstructor() {
        GetConfigRequest request = new GetConfigRequest();
        assertNull(request.getDataId());
        assertNull(request.getGroup());
        assertEquals(0, request.getTimeoutMs());
        assertNull(request.getLocalMd5());
    }

    @Test
    @DisplayName("test builder with all fields")
    void testBuilderWithAllFields() {
        GetConfigRequest request = GetConfigRequest.builder()
            .dataId("test-data-id")
            .group("test-group")
            .timeoutMs(5000L)
            .localMd5("abc123md5")
            .build();

        assertEquals("test-data-id", request.getDataId());
        assertEquals("test-group", request.getGroup());
        assertEquals(5000L, request.getTimeoutMs());
        assertEquals("abc123md5", request.getLocalMd5());
    }

    @Test
    @DisplayName("test builder without localMd5")
    void testBuilderWithoutLocalMd5() {
        GetConfigRequest request = GetConfigRequest.builder()
            .dataId("test-data-id")
            .group("test-group")
            .timeoutMs(3000L)
            .build();

        assertEquals("test-data-id", request.getDataId());
        assertEquals("test-group", request.getGroup());
        assertEquals(3000L, request.getTimeoutMs());
        assertNull(request.getLocalMd5());
    }

    @Test
    @DisplayName("test getter and setter")
    void testGetterAndSetter() {
        GetConfigRequest request = new GetConfigRequest();
        request.setDataId("data-id");
        request.setGroup("group");
        request.setTimeoutMs(10000L);
        request.setLocalMd5("local-md5-hash");

        assertEquals("data-id", request.getDataId());
        assertEquals("group", request.getGroup());
        assertEquals(10000L, request.getTimeoutMs());
        assertEquals("local-md5-hash", request.getLocalMd5());
    }

    @Test
    @DisplayName("test toString contains all fields")
    void testToString() {
        GetConfigRequest request = GetConfigRequest.builder()
            .dataId("test-id")
            .group("test-group")
            .timeoutMs(5000L)
            .localMd5("hash123")
            .build();

        String str = request.toString();
        assertEquals("GetConfigRequest{dataId='test-id', group='test-group', timeoutMs=5000, "
            + "localMd5='hash123'}", str);
    }
}
