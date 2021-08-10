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

package com.alibaba.nacos.client.naming.core;

import org.junit.Assert;
import org.junit.Test;

public class ProtectModeTest {
    
    @Test
    public void testProtectThresholdDefault() {
        final ProtectMode protectMode = new ProtectMode();
        Assert.assertEquals(0.8f, protectMode.getProtectThreshold(), 0.01f);
    }
    
    @Test
    public void testSetProtectThreshold() {
        final ProtectMode protectMode = new ProtectMode();
        float expect = 0.7f;
        protectMode.setProtectThreshold(expect);
        Assert.assertEquals(expect, protectMode.getProtectThreshold(), 0.01f);
    }
}