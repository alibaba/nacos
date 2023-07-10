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

package com.alibaba.nacos.config.server.constant;

import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.persistence.constants.PersistenceConstant;
import com.alibaba.nacos.plugin.datasource.constants.CommonConstant;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.module.ModuleState;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * config module state builder test.
 *
 * @author 985492783@qq.com
 * @date 2023/4/7 23:34
 */
public class ConfigModuleStateBuilderTest {
    
    private ConfigurableEnvironment environment;
    
    @Before
    public void setUp() {
        environment = new MockEnvironment()
                .withProperty(PersistenceConstant.DATASOURCE_PLATFORM_PROPERTY, PersistenceConstant.DERBY)
                .withProperty(CommonConstant.NACOS_PLUGIN_DATASOURCE_LOG, "true");
        EnvUtil.setEnvironment(environment);
    }
    
    @Test
    public void testBuild() {
        ModuleState actual = new ConfigModuleStateBuilder().build();
        Map<String, Object> states = actual.getStates();
        assertEquals(PersistenceConstant.DERBY, states.get(Constants.DATASOURCE_PLATFORM_PROPERTY_STATE));
        assertEquals(true, states.get(Constants.NACOS_PLUGIN_DATASOURCE_LOG_STATE));
        assertEquals(PropertyUtil.getNotifyConnectTimeout(), states.get(PropertiesConstant.NOTIFY_CONNECT_TIMEOUT));
        assertEquals(PropertyUtil.getNotifySocketTimeout(), states.get(PropertiesConstant.NOTIFY_SOCKET_TIMEOUT));
        assertEquals(PropertyUtil.isHealthCheck(), states.get(PropertiesConstant.IS_HEALTH_CHECK));
        assertEquals(PropertyUtil.getMaxHealthCheckFailCount(),
                states.get(PropertiesConstant.MAX_HEALTH_CHECK_FAIL_COUNT));
        assertEquals(PropertyUtil.getMaxContent(), states.get(PropertiesConstant.MAX_CONTENT));
        assertEquals(PropertyUtil.isManageCapacity(), states.get(PropertiesConstant.IS_MANAGE_CAPACITY));
        assertEquals(PropertyUtil.isCapacityLimitCheck(), states.get(PropertiesConstant.IS_CAPACITY_LIMIT_CHECK));
        assertEquals(PropertyUtil.getDefaultClusterQuota(), states.get(PropertiesConstant.DEFAULT_CLUSTER_QUOTA));
        assertEquals(PropertyUtil.getDefaultGroupQuota(), states.get(PropertiesConstant.DEFAULT_GROUP_QUOTA));
        assertEquals(PropertyUtil.getDefaultMaxSize(), states.get(PropertiesConstant.DEFAULT_MAX_SIZE));
        assertEquals(PropertyUtil.getDefaultMaxAggrCount(), states.get(PropertiesConstant.DEFAULT_MAX_AGGR_COUNT));
        assertEquals(PropertyUtil.getDefaultMaxAggrSize(), states.get(PropertiesConstant.DEFAULT_MAX_AGGR_SIZE));
    }
}
