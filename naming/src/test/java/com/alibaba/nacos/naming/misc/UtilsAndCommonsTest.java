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

package com.alibaba.nacos.naming.misc;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import static com.alibaba.nacos.naming.misc.UtilsAndCommons.DEFAULT_NACOS_NAMING_CONTEXT;
import static com.alibaba.nacos.naming.misc.UtilsAndCommons.NACOS_NAMING_CONTEXT;

public class UtilsAndCommonsTest {
    
    @Test
    public void testControllerPathsDefaultValues() {
        
        MockEnvironment environment = new MockEnvironment();
        
        Assert.assertEquals(DEFAULT_NACOS_NAMING_CONTEXT, environment.resolvePlaceholders(NACOS_NAMING_CONTEXT));
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testShakeUpException() {
        UtilsAndCommons.shakeUp(null, 0);
    }
    
    @Test
    public void testShakeUp() {
        Assert.assertEquals(0, UtilsAndCommons.shakeUp(null, 1));
        char[] chars = new char[] {2325, 9, 30, 12, 2};
        Assert.assertEquals(0, UtilsAndCommons.shakeUp(new String(chars), 1));
    }
}
