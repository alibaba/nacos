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
 *
 */

package com.alibaba.nacos.ai.utils;

import com.alibaba.nacos.ai.model.hub.HubResourceReference;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HubResourceReferenceJsonUtilTest {
    
    @Test
    void testBuildAndRoundTrip() throws NacosException {
        HubResourceReference reference =
                HubResourceReferenceJsonUtil.build(
                        "222",
                        101L,
                        "find-skills",
                        501L,
                        "1.0.0");
        
        String json = HubResourceReferenceJsonUtil.toJson(reference);
        HubResourceReference parsed =
                HubResourceReferenceJsonUtil.parse(json);
        
        assertEquals(1, parsed.getSchemaVersion());
        assertEquals("HUB_REFERENCE", parsed.getEntryKind());
        assertEquals("222", parsed.getSourceNamespaceId());
        assertEquals(101L, parsed.getSourceResourceId());
        assertEquals("find-skills", parsed.getSourceName());
        assertEquals(501L, parsed.getSourceVersionId());
        assertEquals("1.0.0", parsed.getSourceVersion());
    }
    
    @Test
    void testMissingSourceResourceIdShouldFail() {
        String json = "{"
                + "\"schemaVersion\":1,"
                + "\"entryKind\":\"HUB_REFERENCE\","
                + "\"sourceNamespaceId\":\"222\","
                + "\"sourceName\":\"find-skills\","
                + "\"sourceVersionId\":501,"
                + "\"sourceVersion\":\"1.0.0\""
                + "}";
        
        assertThrows(NacosException.class,
                () -> HubResourceReferenceJsonUtil.parse(json));
    }
    
    @Test
    void testWrongEntryKindShouldFail() {
        String json = "{"
                + "\"schemaVersion\":1,"
                + "\"entryKind\":\"NORMAL_STORAGE\","
                + "\"sourceNamespaceId\":\"222\","
                + "\"sourceResourceId\":101,"
                + "\"sourceName\":\"find-skills\","
                + "\"sourceVersionId\":501,"
                + "\"sourceVersion\":\"1.0.0\""
                + "}";
        
        assertThrows(NacosException.class,
                () -> HubResourceReferenceJsonUtil.parse(json));
    }
    
    @Test
    void testUnsupportedSchemaVersionShouldFail() {
        String json = "{"
                + "\"schemaVersion\":2,"
                + "\"entryKind\":\"HUB_REFERENCE\","
                + "\"sourceNamespaceId\":\"222\","
                + "\"sourceResourceId\":101,"
                + "\"sourceName\":\"find-skills\","
                + "\"sourceVersionId\":501,"
                + "\"sourceVersion\":\"1.0.0\""
                + "}";
        
        assertThrows(NacosException.class,
                () -> HubResourceReferenceJsonUtil.parse(json));
    }
    
    @Test
    void testMalformedJsonShouldFail() {
        assertThrows(NacosException.class,
                () -> HubResourceReferenceJsonUtil.parse("{"));
    }
}