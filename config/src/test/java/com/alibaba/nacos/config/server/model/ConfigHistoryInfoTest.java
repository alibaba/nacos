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

package com.alibaba.nacos.config.server.model;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ConfigHistoryInfoTest {
    
    @Test
    void testAccessorsEqualsAndHashCode() {
        ConfigHistoryInfo first = buildConfigHistoryInfo();
        ConfigHistoryInfo second = buildConfigHistoryInfo();
        
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        
        first.setLastId(2L);
        
        assertEquals(2L, first.getLastId());
        assertNotEquals(first, second);
        assertNotEquals(first, null);
        assertNotEquals(first, "history");
    }
    
    private ConfigHistoryInfo buildConfigHistoryInfo() {
        Timestamp now = new Timestamp(1000L);
        ConfigHistoryInfo result = new ConfigHistoryInfo();
        result.setId(1L);
        result.setLastId(1L);
        result.setDataId("dataId");
        result.setGroup("group");
        result.setTenant("tenant");
        result.setAppName("app");
        result.setMd5("md5");
        result.setContent("content");
        result.setSrcIp("127.0.0.1");
        result.setSrcUser("user");
        result.setOpType("I");
        result.setPublishType("formal");
        result.setGrayName("gray");
        result.setExtInfo("{}");
        result.setCreatedTime(now);
        result.setLastModifiedTime(now);
        result.setEncryptedDataKey("key");
        return result;
    }
}
