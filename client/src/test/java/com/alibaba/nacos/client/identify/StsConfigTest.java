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

package com.alibaba.nacos.client.identify;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class StsConfigTest {
    
    @After
    public void after() {
        StsConfig.getInstance().setRamRoleName(null);
        StsConfig.getInstance().setTimeToRefreshInMillisecond(3 * 60 * 1000);
        StsConfig.getInstance().setCacheSecurityCredentials(true);
        StsConfig.getInstance().setSecurityCredentials(null);
        StsConfig.getInstance().setSecurityCredentialsUrl(null);
    }
    
    @Test
    public void testGetInstance() {
        StsConfig instance1 = StsConfig.getInstance();
        StsConfig instance2 = StsConfig.getInstance();
        Assert.assertEquals(instance1, instance2);
        
    }
    
    @Test
    public void testGetRamRoleName() {
        StsConfig.getInstance().setRamRoleName("test");
        Assert.assertEquals("test", StsConfig.getInstance().getRamRoleName());
        
    }
    
    @Test
    public void testGetTimeToRefreshInMillisecond() {
        Assert.assertEquals(3 * 60 * 1000, StsConfig.getInstance().getTimeToRefreshInMillisecond());
        StsConfig.getInstance().setTimeToRefreshInMillisecond(3000);
        Assert.assertEquals(3000, StsConfig.getInstance().getTimeToRefreshInMillisecond());
    }
    
    @Test
    public void testGetSecurityCredentialsUrl() {
        Assert.assertNull(StsConfig.getInstance().getSecurityCredentialsUrl());
        String expect = "localhost";
        StsConfig.getInstance().setSecurityCredentialsUrl(expect);
        Assert.assertEquals(expect, StsConfig.getInstance().getSecurityCredentialsUrl());
    }
    
    @Test
    public void testGetSecurityCredentials() {
        Assert.assertNull(StsConfig.getInstance().getSecurityCredentials());
        String expect = "abc";
        StsConfig.getInstance().setSecurityCredentials(expect);
        Assert.assertEquals(expect, StsConfig.getInstance().getSecurityCredentials());
    }
    
    @Test
    public void testIsCacheSecurityCredentials() {
        Assert.assertTrue(StsConfig.getInstance().isCacheSecurityCredentials());
        StsConfig.getInstance().setCacheSecurityCredentials(false);
        Assert.assertFalse(StsConfig.getInstance().isCacheSecurityCredentials());
    }
    
    @Test
    public void testIsOnFalse() {
        boolean stsOn = StsConfig.getInstance().isStsOn();
        Assert.assertFalse(stsOn);
    }
    
    @Test
    public void testIsOnTrue() {
        StsConfig.getInstance().setSecurityCredentials("abc");
        boolean stsOn = StsConfig.getInstance().isStsOn();
        Assert.assertTrue(stsOn);
    }
    
}