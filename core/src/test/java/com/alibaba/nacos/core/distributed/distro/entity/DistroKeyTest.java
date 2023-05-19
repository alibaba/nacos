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

package com.alibaba.nacos.core.distributed.distro.entity;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class DistroKeyTest {
    
    private final String type = "com.alibaba.nacos.naming.iplist.";
    
    private final String resourceKey = "checksum";
    
    private final String targetServer = "1.1.1.1";
    
    private DistroKey distroKey1;
    
    private DistroKey distroKey2;
    
    @Before
    public void setUp() {
        distroKey1 = new DistroKey(resourceKey, type, targetServer);
        distroKey2 = new DistroKey();
        distroKey2.setResourceKey(resourceKey);
        distroKey2.setResourceType(type);
        distroKey2.setTargetServer(targetServer);
    }
    
    @Test
    public void testGetters() {
        Assert.assertEquals(distroKey2.getResourceKey(), resourceKey);
        Assert.assertEquals(distroKey2.getResourceType(), type);
        Assert.assertEquals(distroKey2.getTargetServer(), targetServer);
    
    }
    
    @Test
    public void testEquals() {
        Assert.assertEquals(distroKey1, distroKey2);
    }
}