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

package com.alibaba.nacos.persistence.repository.embedded;

import com.alibaba.nacos.persistence.repository.embedded.sql.ModifyRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddedStorageContextHolderTest {
    
    private static final String TEST_SQL = "SELECT * FROM config_info";
    
    @BeforeEach
    void setUp() {
    }
    
    @AfterEach
    void tearDown() {
        EmbeddedStorageContextHolder.cleanAllContext();
    }
    
    @Test
    void testAddSqlContextRollbackOnUpdateFail() {
        EmbeddedStorageContextHolder.addSqlContext(true, TEST_SQL, "test");
        List<ModifyRequest> requests = EmbeddedStorageContextHolder.getCurrentSqlContext();
        assertEquals(1, requests.size());
        assertEquals(TEST_SQL, requests.get(0).getSql());
        assertEquals(0, requests.get(0).getExecuteNo());
        assertEquals("test", requests.get(0).getArgs()[0]);
        assertTrue(requests.get(0).isRollBackOnUpdateFail());
    }
    
    @Test
    void testPutExtendInfo() {
        EmbeddedStorageContextHolder.putExtendInfo("testPutExtendInfo", "test_value");
        assertTrue(EmbeddedStorageContextHolder.containsExtendInfo("testPutExtendInfo"));
        assertEquals("test_value", EmbeddedStorageContextHolder.getCurrentExtendInfo().get("testPutExtendInfo"));
    }
    
    @Test
    void testPutAllExtendInfo() {
        Map<String, String> map = new HashMap<>();
        map.put("testPutAllExtendInfo", "test_value");
        EmbeddedStorageContextHolder.putAllExtendInfo(map);
        assertTrue(EmbeddedStorageContextHolder.containsExtendInfo("testPutAllExtendInfo"));
        assertEquals("test_value", EmbeddedStorageContextHolder.getCurrentExtendInfo().get("testPutAllExtendInfo"));
    }
}