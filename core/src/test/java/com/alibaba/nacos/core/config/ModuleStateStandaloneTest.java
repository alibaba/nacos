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

import com.alibaba.nacos.core.distributed.distro.DistroConstants;
import com.alibaba.nacos.core.distributed.raft.RaftSysConstants;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.module.ModuleStateHolder;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * cluster module-state-builder test.
 *
 * @author 985492783@qq.com
 * @date 2023/4/8 0:13
 */
class ModuleStateStandaloneTest {
    
    private ConfigurableEnvironment environment;
    
    private ModuleStateHolder moduleStateHolder;
    
    @Test
    void testStandaloneBuilder() throws InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchMethodException {
        try (MockedStatic<EnvUtil> mockedStatic = Mockito.mockStatic(EnvUtil.class)) {
            environment = new MockEnvironment();
            mockedStatic.when(EnvUtil::getEnvironment).thenReturn(environment);
            mockedStatic.when(EnvUtil::getStandaloneMode).thenReturn(true);
            
            Constructor<ModuleStateHolder> constructor = ModuleStateHolder.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            moduleStateHolder = constructor.newInstance();
            
            assertFalse(moduleStateHolder.getModuleState(DistroConstants.DISTRO_MODULE).isPresent());
            assertFalse(moduleStateHolder.getModuleState(RaftSysConstants.RAFT_STATE).isPresent());
        }
    }
    
}
