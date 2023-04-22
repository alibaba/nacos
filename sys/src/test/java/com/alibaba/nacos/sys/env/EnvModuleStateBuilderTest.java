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

package com.alibaba.nacos.sys.env;

import com.alibaba.nacos.common.utils.VersionUtils;
import com.alibaba.nacos.sys.module.ModuleState;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class EnvModuleStateBuilderTest {
    
    @BeforeClass
    public static void setUp() throws Exception {
        System.setProperty(Constants.STANDALONE_MODE_PROPERTY_NAME, "true");
        EnvUtil.setIsStandalone(null);
    }
    
    @Test
    public void testBuild() {
        ModuleState actual = new EnvModuleStateBuilder().build();
        assertEquals(Constants.SYS_MODULE, actual.getModuleName());
        assertEquals(EnvUtil.STANDALONE_MODE_ALONE, actual.getStates().get(Constants.STANDALONE_MODE_STATE));
        assertNull(EnvUtil.FUNCTION_MODE_NAMING, actual.getStates().get(Constants.FUNCTION_MODE_STATE));
        assertEquals(VersionUtils.version, actual.getStates().get(Constants.NACOS_VERSION));
    }
}