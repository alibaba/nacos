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

class PublishConfigRequestTest {

    @Test
    @DisplayName("test default constructor")
    void testDefaultConstructor() {
        PublishConfigRequest request = new PublishConfigRequest();
        assertNull(request.getDataId());
        assertNull(request.getGroup());
        assertNull(request.getContent());
        assertNull(request.getType());
        assertNull(request.getCasMd5());
    }

    @Test
    @DisplayName("test builder with all fields including casMd5")
    void testBuilderWithAllFields() {
        PublishConfigRequest request = PublishConfigRequest.builder()
            .dataId("test-data-id")
            .group("test-group")
            .content("config content")
            .type("yaml")
            .casMd5("prev-md5-hash")
            .build();

        assertEquals("test-data-id", request.getDataId());
        assertEquals("test-group", request.getGroup());
        assertEquals("config content", request.getContent());
        assertEquals("yaml", request.getType());
        assertEquals("prev-md5-hash", request.getCasMd5());
    }

    @Test
    @DisplayName("test builder without casMd5 for normal publish")
    void testBuilderWithoutCasMd5() {
        PublishConfigRequest request = PublishConfigRequest.builder()
            .dataId("test-data-id")
            .group("test-group")
            .content("config content")
            .type("properties")
            .build();

        assertEquals("test-data-id", request.getDataId());
        assertEquals("test-group", request.getGroup());
        assertEquals("config content", request.getContent());
        assertEquals("properties", request.getType());
        assertNull(request.getCasMd5());
    }

    @Test
    @DisplayName("test getter and setter")
    void testGetterAndSetter() {
        PublishConfigRequest request = new PublishConfigRequest();
        request.setDataId("data-id");
        request.setGroup("group");
        request.setContent("content");
        request.setType("json");
        request.setCasMd5("cas-md5");

        assertEquals("data-id", request.getDataId());
        assertEquals("group", request.getGroup());
        assertEquals("content", request.getContent());
        assertEquals("json", request.getType());
        assertEquals("cas-md5", request.getCasMd5());
    }

    @Test
    @DisplayName("test toString contains key fields")
    void testToString() {
        PublishConfigRequest request = PublishConfigRequest.builder()
            .dataId("test-id")
            .group("test-group")
            .content("test-content")
            .type("yaml")
            .casMd5("hash123")
            .build();

        String str = request.toString();
        assertEquals("PublishConfigRequest{dataId='test-id', group='test-group', type='yaml', "
            + "casMd5='hash123', content length=12}", str);
    }
}
