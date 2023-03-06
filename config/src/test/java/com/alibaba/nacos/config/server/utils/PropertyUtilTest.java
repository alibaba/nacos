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

package com.alibaba.nacos.config.server.utils;

import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.mockito.ArgumentMatchers.eq;

@RunWith(SpringJUnit4ClassRunner.class)
public class PropertyUtilTest {
    
    @Mock
    private ConfigurableEnvironment configurableEnvironment;
    
    @Test
    public void testGetPropertyV1() {
        MockedStatic<EnvUtil> envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        
        EnvUtil.setEnvironment(configurableEnvironment);
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(eq("test"))).thenReturn("test");
        Assert.assertEquals("test", new PropertyUtil().getProperty("test"));
        
        envUtilMockedStatic.close();
    }
    
    @Test
    public void testGetPropertyV2() {
        MockedStatic<EnvUtil> envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        
        EnvUtil.setEnvironment(configurableEnvironment);
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(eq("test"), eq("default"))).thenReturn("default");
        Assert.assertEquals("default", new PropertyUtil().getProperty("test", "default"));
    
        envUtilMockedStatic.close();
    }
    
}
