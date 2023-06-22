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
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.module.ModuleState;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * distro module state builder test.
 * @author 985492783@qq.com
 * @date 2023/4/7 23:51
 */
public class DistroModuleStateBuilderTest {
    
    private ConfigurableEnvironment environment;
    
    @Before
    public void setUp() {
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
    }
    
    @Test
    public void testBuild() {
        ModuleState actual = new DistroModuleStateBuilder().build();
        Map<String, Object> states = actual.getStates();
        assertEquals(DistroConstants.DISTRO_MODULE, actual.getModuleName());
        assertEquals(DistroConstants.DEFAULT_DATA_SYNC_DELAY_MILLISECONDS,
                states.get(DistroConstants.DATA_SYNC_DELAY_MILLISECONDS_STATE));
        assertEquals(DistroConstants.DEFAULT_DATA_SYNC_TIMEOUT_MILLISECONDS,
                states.get(DistroConstants.DATA_SYNC_TIMEOUT_MILLISECONDS_STATE));
        assertEquals(DistroConstants.DEFAULT_DATA_SYNC_RETRY_DELAY_MILLISECONDS,
                states.get(DistroConstants.DATA_SYNC_RETRY_DELAY_MILLISECONDS_STATE));
        assertEquals(DistroConstants.DEFAULT_DATA_VERIFY_INTERVAL_MILLISECONDS,
                states.get(DistroConstants.DATA_VERIFY_INTERVAL_MILLISECONDS_STATE));
        assertEquals(DistroConstants.DEFAULT_DATA_VERIFY_TIMEOUT_MILLISECONDS,
                states.get(DistroConstants.DATA_VERIFY_TIMEOUT_MILLISECONDS_STATE));
        assertEquals(DistroConstants.DEFAULT_DATA_LOAD_RETRY_DELAY_MILLISECONDS,
                states.get(DistroConstants.DATA_LOAD_RETRY_DELAY_MILLISECONDS_STATE));
        assertEquals(DistroConstants.DEFAULT_DATA_LOAD_TIMEOUT_MILLISECONDS,
                states.get(DistroConstants.DATA_LOAD_TIMEOUT_MILLISECONDS_STATE));
    }
}
