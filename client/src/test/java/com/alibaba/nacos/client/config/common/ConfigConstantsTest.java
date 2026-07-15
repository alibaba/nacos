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

package com.alibaba.nacos.client.config.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConfigConstantsTest {
    
    @Test
    void testConstructor() {
        assertNotNull(new ConfigConstants());
    }
    
    @Test
    void testFields() {
        assertEquals("tenant", ConfigConstants.TENANT);
        assertEquals("dataId", ConfigConstants.DATA_ID);
        assertEquals("group", ConfigConstants.GROUP);
        assertEquals("content", ConfigConstants.CONTENT);
        assertEquals("configType", ConfigConstants.CONFIG_TYPE);
        assertEquals("encryptedDataKey", ConfigConstants.ENCRYPTED_DATA_KEY);
        assertEquals("type", ConfigConstants.TYPE);
        assertEquals("md5", ConfigConstants.MD5);
    }
}
