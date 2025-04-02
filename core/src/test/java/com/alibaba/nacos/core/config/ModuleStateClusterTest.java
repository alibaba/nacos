/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.core.config;

import com.alibaba.nacos.core.auth.AuthModuleStateBuilder;
import com.alibaba.nacos.core.distributed.distro.DistroConstants;
import com.alibaba.nacos.core.distributed.raft.RaftSysConstants;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.sys.env.DeploymentType;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.module.ModuleStateHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * standalone module-state-builder test.
 *
 * @author 985492783@qq.com
 * @date 2023/4/8 0:17
 */
class ModuleStateClusterTest {
    
    private MockEnvironment environment;
    
    private ModuleStateHolder moduleStateHolder;
    
    @BeforeEach
    void setUp()
            throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        environment = new MockEnvironment();
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE, "nacos");
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_KEY, "111");
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_VALUE, "111");
        EnvUtil.setEnvironment(environment);
        EnvUtil.setIsStandalone(false);
        EnvUtil.setDeploymentType(DeploymentType.MERGED);
        Constructor<ModuleStateHolder> constructor = ModuleStateHolder.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        moduleStateHolder = constructor.newInstance();
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(null);
        EnvUtil.setIsStandalone(null);
        EnvUtil.setDeploymentType(null);
    }
    
    @Test
    void testStandaloneBuilder() {
        assertTrue(moduleStateHolder.getModuleState(DistroConstants.DISTRO_MODULE).isPresent());
        assertTrue(moduleStateHolder.getModuleState(RaftSysConstants.RAFT_STATE).isPresent());
        assertTrue(moduleStateHolder.getModuleState(AuthModuleStateBuilder.AUTH_MODULE).isPresent());
    }
}
