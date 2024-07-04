/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.api;

import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequestResourceTest {
    
    @BeforeEach
    void setUp() throws Exception {
    }
    
    @Test
    void testBuildNamingRequestResource() {
        RequestResource actual = RequestResource.namingBuilder().setNamespace("NS").setGroup("G").setResource("Service").build();
        assertEquals(SignType.NAMING, actual.getType());
        assertEquals("NS", actual.getNamespace());
        assertEquals("G", actual.getGroup());
        assertEquals("Service", actual.getResource());
    }
    
    @Test
    void testBuildConfigRequestResource() {
        RequestResource actual = RequestResource.configBuilder().setNamespace("NS").setGroup("G").setResource("dataId").build();
        assertEquals(SignType.CONFIG, actual.getType());
        assertEquals("NS", actual.getNamespace());
        assertEquals("G", actual.getGroup());
        assertEquals("dataId", actual.getResource());
    }
}
