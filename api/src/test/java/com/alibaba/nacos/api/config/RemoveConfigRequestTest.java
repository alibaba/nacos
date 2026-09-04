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

class RemoveConfigRequestTest {

    @Test
    @DisplayName("test default constructor")
    void testDefaultConstructor() {
        RemoveConfigRequest request = new RemoveConfigRequest();
        assertNull(request.getDataId());
        assertNull(request.getGroup());
    }

    @Test
    @DisplayName("test builder with all fields")
    void testBuilderWithAllFields() {
        RemoveConfigRequest request = RemoveConfigRequest.builder()
            .dataId("test-data-id")
            .group("test-group")
            .build();

        assertEquals("test-data-id", request.getDataId());
        assertEquals("test-group", request.getGroup());
    }

    @Test
    @DisplayName("test getter and setter")
    void testGetterAndSetter() {
        RemoveConfigRequest request = new RemoveConfigRequest();
        request.setDataId("data-id");
        request.setGroup("group");

        assertEquals("data-id", request.getDataId());
        assertEquals("group", request.getGroup());
    }

    @Test
    @DisplayName("test toString")
    void testToString() {
        RemoveConfigRequest request = RemoveConfigRequest.builder()
            .dataId("test-id")
            .group("test-group")
            .build();

        String str = request.toString();
        assertEquals("RemoveConfigRequest{dataId='test-id', group='test-group'}", str);
    }
}
