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

package com.alibaba.nacos.config.server.model;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConfigHistoryInfoDetailTest {
    
    @Test
    void testSettersAndGetters() {
        ConfigHistoryInfoDetail detail = new ConfigHistoryInfoDetail();
        detail.setId(1L);
        detail.setLastId(2L);
        detail.setDataId("dataId");
        detail.setGroup("group");
        detail.setTenant("tenant");
        detail.setOpType("I");
        detail.setPublishType("formal");
        detail.setGrayName("gray");
        detail.setAppName("app");
        detail.setSrcIp("1.1.1.1");
        detail.setSrcUser("user");
        detail.setOriginalMd5("omd5");
        detail.setOriginalContent("ocontent");
        detail.setOriginalEncryptedDataKey("okey");
        detail.setOriginalExtInfo("oext");
        detail.setUpdatedMd5("umd5");
        detail.setUpdatedContent("ucontent");
        detail.setUpdatedEncryptedDataKey("ukey");
        detail.setUpdateExtInfo("uext");
        Timestamp ts = new Timestamp(System.currentTimeMillis());
        detail.setCreatedTime(ts);
        detail.setLastModifiedTime(ts);
        
        assertEquals(1L, detail.getId());
        assertEquals(2L, detail.getLastId());
        assertEquals("dataId", detail.getDataId());
        assertEquals("group", detail.getGroup());
        assertEquals("tenant", detail.getTenant());
        assertEquals("I", detail.getOpType());
        assertEquals("formal", detail.getPublishType());
        assertEquals("gray", detail.getGrayName());
        assertEquals("app", detail.getAppName());
        assertEquals("1.1.1.1", detail.getSrcIp());
        assertEquals("user", detail.getSrcUser());
        assertEquals("omd5", detail.getOriginalMd5());
        assertEquals("ocontent", detail.getOriginalContent());
        assertEquals("okey", detail.getOriginalEncryptedDataKey());
        assertEquals("oext", detail.getOriginalExtInfo());
        assertEquals("umd5", detail.getUpdatedMd5());
        assertEquals("ucontent", detail.getUpdatedContent());
        assertEquals("ukey", detail.getUpdatedEncryptedDataKey());
        assertEquals("uext", detail.getUpdateExtInfo());
        assertEquals(ts, detail.getCreatedTime());
        assertEquals(ts, detail.getLastModifiedTime());
    }
    
    @Test
    void testDefaults() {
        ConfigHistoryInfoDetail detail = new ConfigHistoryInfoDetail();
        assertEquals(0L, detail.getId());
        assertEquals(-1L, detail.getLastId());
        assertNull(detail.getDataId());
        assertNull(detail.getCreatedTime());
    }
}
