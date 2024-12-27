/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.remote.request;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.constant.AbilityStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RequestMetaTest {
    
    private RequestMeta requestMeta;
    
    @BeforeEach
    void setUp() {
        requestMeta = new RequestMeta();
        requestMeta.setClientIp("127.0.0.1");
        requestMeta.setClientVersion("1.0.0");
        requestMeta.setConnectionId("test-connection-id");
        Map<String, String> labels = new HashMap<>();
        labels.put("env", "dev");
        requestMeta.setLabels(labels);
    }
    
    @Test
    void testGetClientIp() {
        assertEquals("127.0.0.1", requestMeta.getClientIp());
    }
    
    @Test
    void testGetClientVersion() {
        assertEquals("1.0.0", requestMeta.getClientVersion());
    }
    
    @Test
    void testGetConnectionId() {
        assertEquals("test-connection-id", requestMeta.getConnectionId());
    }
    
    @Test
    void testGetLabels() {
        Map<String, String> labels = requestMeta.getLabels();
        assertNotNull(labels);
        assertEquals(1, labels.size());
        assertEquals("dev", labels.get("env"));
    }
    
    @Test
    void testToString() {
        String expected = "RequestMeta{connectionId='test-connection-id', clientIp='127.0.0.1', clientVersion='1.0.0', labels={env=dev}}";
        assertEquals(expected, requestMeta.toString());
    }
    
    @Test
    void testGetConnectionAbilityForNonExist() {
        assertEquals(AbilityStatus.UNKNOWN, requestMeta.getConnectionAbility(AbilityKey.SERVER_TEST_1));
        requestMeta.setAbilityTable(Collections.emptyMap());
        assertEquals(AbilityStatus.UNKNOWN, requestMeta.getConnectionAbility(AbilityKey.SERVER_TEST_1));
    }
    
    @Test
    void testGetConnectionAbilityForExist() {
        requestMeta.setAbilityTable(Collections.singletonMap(AbilityKey.SERVER_TEST_1.getName(), Boolean.FALSE));
        assertEquals(AbilityStatus.NOT_SUPPORTED, requestMeta.getConnectionAbility(AbilityKey.SERVER_TEST_1));
        requestMeta.setAbilityTable(Collections.singletonMap(AbilityKey.SERVER_TEST_1.getName(), Boolean.TRUE));
        assertEquals(AbilityStatus.SUPPORTED, requestMeta.getConnectionAbility(AbilityKey.SERVER_TEST_1));
    }
}
