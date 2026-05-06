/*
 *   Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.alibaba.nacos.client.auth.ram.identify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StsConfigTest {
    
    private static void resetStsConfig() {
        StsConfig.getInstance().setRamRoleName(null);
        StsConfig.getInstance().setTimeToRefreshInMillisecond(3 * 60 * 1000);
        StsConfig.getInstance().setCacheSecurityCredentials(true);
        StsConfig.getInstance().setSecurityCredentials(null);
        StsConfig.getInstance().setSecurityCredentialsUrl(null);
        System.clearProperty(IdentifyConstants.RAM_ROLE_NAME_PROPERTY);
        System.clearProperty(IdentifyConstants.REFRESH_TIME_PROPERTY);
        System.clearProperty(IdentifyConstants.SECURITY_PROPERTY);
        System.clearProperty(IdentifyConstants.SECURITY_URL_PROPERTY);
        System.clearProperty(IdentifyConstants.SECURITY_CACHE_PROPERTY);
    }
    
    @BeforeEach
    void before() {
        resetStsConfig();
    }
    
    @AfterEach
    void after() {
        resetStsConfig();
    }
    
    @Test
    void testGetInstance() {
        StsConfig instance1 = StsConfig.getInstance();
        StsConfig instance2 = StsConfig.getInstance();
        assertEquals(instance1, instance2);
        
    }
    
    @Test
    void testGetRamRoleName() {
        StsConfig.getInstance().setRamRoleName("test");
        assertEquals("test", StsConfig.getInstance().getRamRoleName());
        
    }
    
    @Test
    void testGetTimeToRefreshInMillisecond() {
        assertEquals(3 * 60 * 1000, StsConfig.getInstance().getTimeToRefreshInMillisecond());
        StsConfig.getInstance().setTimeToRefreshInMillisecond(3000);
        assertEquals(3000, StsConfig.getInstance().getTimeToRefreshInMillisecond());
    }
    
    @Test
    void testGetSecurityCredentialsUrl() {
        assertNull(StsConfig.getInstance().getSecurityCredentialsUrl());
        String expect = "localhost";
        StsConfig.getInstance().setSecurityCredentialsUrl(expect);
        assertEquals(expect, StsConfig.getInstance().getSecurityCredentialsUrl());
    }
    
    @Test
    void testGetSecurityCredentialsUrlDefault() {
        StsConfig.getInstance().setRamRoleName("test");
        assertEquals("http://100.100.100.200/latest/meta-data/ram/security-credentials/test",
                StsConfig.getInstance().getSecurityCredentialsUrl());
    }
    
    @Test
    void testGetSecurityCredentials() {
        assertNull(StsConfig.getInstance().getSecurityCredentials());
        String expect = "abc";
        StsConfig.getInstance().setSecurityCredentials(expect);
        assertEquals(expect, StsConfig.getInstance().getSecurityCredentials());
    }
    
    @Test
    void testIsCacheSecurityCredentials() {
        assertTrue(StsConfig.getInstance().isCacheSecurityCredentials());
        StsConfig.getInstance().setCacheSecurityCredentials(false);
        assertFalse(StsConfig.getInstance().isCacheSecurityCredentials());
    }
    
    @Test
    void testIsOnFalse() {
        boolean stsOn = StsConfig.getInstance().isStsOn();
        assertFalse(stsOn);
    }
    
    @Test
    void testIsOnTrue() {
        StsConfig.getInstance().setSecurityCredentials("abc");
        boolean stsOn = StsConfig.getInstance().isStsOn();
        assertTrue(stsOn);
    }
    
    @Test
    void testFromEnv()
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Constructor<StsConfig> constructor = StsConfig.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        System.setProperty(IdentifyConstants.RAM_ROLE_NAME_PROPERTY, "test");
        System.setProperty(IdentifyConstants.REFRESH_TIME_PROPERTY, "3000");
        System.setProperty(IdentifyConstants.SECURITY_PROPERTY, "abc");
        System.setProperty(IdentifyConstants.SECURITY_URL_PROPERTY, "localhost");
        System.setProperty(IdentifyConstants.SECURITY_CACHE_PROPERTY, "false");
        StsConfig stsConfig = constructor.newInstance();
        assertEquals("test", stsConfig.getRamRoleName());
        assertEquals(3000, stsConfig.getTimeToRefreshInMillisecond());
        assertEquals("abc", stsConfig.getSecurityCredentials());
        assertEquals("localhost", stsConfig.getSecurityCredentialsUrl());
        assertFalse(stsConfig.isCacheSecurityCredentials());
        
    }
}
