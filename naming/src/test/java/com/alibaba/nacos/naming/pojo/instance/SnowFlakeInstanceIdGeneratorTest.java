/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.env.MockEnvironment;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;

/**
 * Created by chenwenshun on 2023/2/24.
 */
@RunWith(MockitoJUnitRunner.class)
public class SnowFlakeInstanceIdGeneratorTest {

    @Mock
    MockEnvironment env;

    @Test
    public void testGenerateInstanceId() {
        MockEnvironment env = Mockito.mock(MockEnvironment.class);
        EnvUtil.setEnvironment(env);
        when(env.getProperty(anyString(), eq(Integer.class), anyInt())).thenReturn(-1);
        SnowFlakeInstanceIdGenerator idGenerator = new SnowFlakeInstanceIdGenerator("hello-service",
                "clusterName", 8080);
        String instanceId = idGenerator.generateInstanceId();
        Assert.assertTrue(instanceId.endsWith("#8080#clusterName#hello-service"));
    }

}
