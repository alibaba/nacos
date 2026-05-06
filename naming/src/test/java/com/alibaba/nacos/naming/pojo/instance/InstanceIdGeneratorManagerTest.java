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

package com.alibaba.nacos.naming.pojo.instance;

import com.alibaba.nacos.api.naming.PreservedMetadataKeys;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.nacos.api.common.Constants.SNOWFLAKE_INSTANCE_ID_GENERATOR;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class InstanceIdGeneratorManagerTest {
    
    static {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("nacos.core.snowflake.worker-id", "-1");
        EnvUtil.setEnvironment(environment);
    }
    
    @Test
    void testGenerateSnowFlakeInstanceId() {
        Instance instance = new Instance();
        Map<String, String> metaData = new HashMap<>(1);
        metaData.put(PreservedMetadataKeys.INSTANCE_ID_GENERATOR, SNOWFLAKE_INSTANCE_ID_GENERATOR);
        instance.setMetadata(metaData);
        instance.setServiceName("service");
        instance.setClusterName("cluster");
        instance.setIp("1.1.1.1");
        instance.setPort(1000);
        String instanceId = InstanceIdGeneratorManager.generateInstanceId(instance);
        assertTrue(instanceId.endsWith("#cluster#service"));
    }
    
    @Test
    void testGenerateInstanceId() {
        Instance instance = new Instance();
        instance.setServiceName("service");
        instance.setClusterName("cluster");
        instance.setIp("1.1.1.1");
        instance.setPort(1000);
        assertThat(InstanceIdGeneratorManager.generateInstanceId(instance), is("1.1.1.1#1000#cluster#service"));
    }
}
