/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.cluster;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MemberMetaDataConstantsTest {
    
    @Test
    void testConstants() {
        assertEquals("raftPort", MemberMetaDataConstants.RAFT_PORT);
        assertEquals("site", MemberMetaDataConstants.SITE_KEY);
        assertEquals("adWeight", MemberMetaDataConstants.AD_WEIGHT);
        assertEquals("weight", MemberMetaDataConstants.WEIGHT);
        assertEquals("lastRefreshTime", MemberMetaDataConstants.LAST_REFRESH_TIME);
        assertEquals("version", MemberMetaDataConstants.VERSION);
        assertEquals("remoteConnectType", MemberMetaDataConstants.SUPPORT_REMOTE_C_TYPE);
        assertEquals("readyToUpgrade", MemberMetaDataConstants.READY_TO_UPGRADE);
        assertEquals("supportGrayModel", MemberMetaDataConstants.SUPPORT_GRAY_MODEL);
    }
    
    @Test
    void testAllConstantsReferenced() {
        String[] keys = MemberMetaDataConstants.BASIC_META_KEYS;
        for (String key : keys) {
            assertNotNull(key);
        }
        assertNotNull(MemberMetaDataConstants.RAFT_PORT);
        assertNotNull(MemberMetaDataConstants.LAST_REFRESH_TIME);
        assertNotNull(MemberMetaDataConstants.SUPPORT_GRAY_MODEL);
    }
    
    @Test
    void testBasicMetaKeys() {
        String[] keys = MemberMetaDataConstants.BASIC_META_KEYS;
        assertNotNull(keys);
        assertEquals(6, keys.length);
        assertEquals(MemberMetaDataConstants.SITE_KEY, keys[0]);
        assertEquals(MemberMetaDataConstants.AD_WEIGHT, keys[1]);
        assertEquals(MemberMetaDataConstants.RAFT_PORT, keys[2]);
        assertEquals(MemberMetaDataConstants.WEIGHT, keys[3]);
        assertEquals(MemberMetaDataConstants.VERSION, keys[4]);
        assertEquals(MemberMetaDataConstants.READY_TO_UPGRADE, keys[5]);
    }
}
