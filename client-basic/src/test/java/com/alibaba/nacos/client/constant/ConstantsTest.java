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

package com.alibaba.nacos.client.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConstantsTest {
    
    @Test
    void testConstructors() {
        assertNotNull(new Constants());
        assertNotNull(new Constants.SysEnv());
        assertNotNull(new Constants.Security());
        assertNotNull(new Constants.Address());
    }
    
    @Test
    void testSysEnvFields() {
        assertEquals("user.home", Constants.SysEnv.USER_HOME);
        assertEquals("project.name", Constants.SysEnv.PROJECT_NAME);
        assertEquals("JM.LOG.PATH", Constants.SysEnv.JM_LOG_PATH);
        assertEquals("JM.SNAPSHOT.PATH", Constants.SysEnv.JM_SNAPSHOT_PATH);
        assertEquals("nacos.env.first", Constants.SysEnv.NACOS_ENV_FIRST);
    }
    
    @Test
    void testSecurityFields() {
        assertEquals(5_000L, Constants.Security.SECURITY_INFO_REFRESH_INTERVAL_MILLS);
    }
    
    @Test
    void testAddressFields() {
        assertEquals(500, Constants.Address.ENDPOINT_SERVER_LIST_PROVIDER_ORDER);
        assertEquals(499, Constants.Address.ADDRESS_SERVER_LIST_PROVIDER_ORDER);
    }
}
