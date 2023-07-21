/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.paramcheck;

import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Constructor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ServerParamCheckConfigTest {
    
    @Test
    public void getConfigFromEnv() throws ReflectiveOperationException {
        MockEnvironment environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        environment.setProperty("nacos.core.param.check.enabled", String.valueOf(false));
        environment.setProperty("nacos.core.param.check.checker", "default");
        
        Constructor<ServerParamCheckConfig> declaredConstructor = ServerParamCheckConfig.class.getDeclaredConstructor();
        declaredConstructor.setAccessible(true);
        ServerParamCheckConfig paramCheckConfig = declaredConstructor.newInstance();
        
        assertFalse(paramCheckConfig.isParamCheckEnabled());
        assertEquals(paramCheckConfig.getActiveParamChecker(), "default");
    }
    
    @Test
    public void setParamCheckEnabled() {
        ServerParamCheckConfig paramCheckConfig = ServerParamCheckConfig.getInstance();
        paramCheckConfig.setParamCheckEnabled(false);
        assertFalse(paramCheckConfig.isParamCheckEnabled());
    }
    
    @Test
    public void setActiveParamChecker() {
        ServerParamCheckConfig paramCheckConfig = ServerParamCheckConfig.getInstance();
        paramCheckConfig.setActiveParamChecker("test");
        assertEquals(paramCheckConfig.getActiveParamChecker(), "test");
    }
}