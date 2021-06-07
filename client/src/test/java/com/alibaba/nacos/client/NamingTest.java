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

package com.alibaba.nacos.client;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.client.naming.NacosNamingService;
import com.alibaba.nacos.client.naming.remote.NamingClientProxy;
import com.alibaba.nacos.common.utils.ThreadUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class NamingTest {
    
    private NamingService namingService;
    
    @Mock
    private NamingClientProxy namingClientProxy;
    
    @Before
    public void startUp() throws NacosException {
        Answer<ServiceInfo> answer = new Answer<ServiceInfo>() {
            public ServiceInfo answer(InvocationOnMock invocation) {
                return null;
            }
        };
        doNothing().when(namingClientProxy).registerService(any(String.class), any(String.class), any(Instance.class));
        doAnswer(answer).when(namingClientProxy).queryInstancesOfService(any(String.class), any(String.class), any(String.class),any(Integer.class), any(Boolean.class));
    }
    
    @Test
    public void testServiceList() throws Exception {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848");
        properties.put(PropertyKeyConst.USERNAME, "nacos");
        properties.put(PropertyKeyConst.PASSWORD, "nacos");
        NamingService namingService = new NacosNamingService(properties);
        ReflectionTestUtils.setField(namingService, "clientProxy", namingClientProxy);
        Instance instance = new Instance();
        instance.setIp("1.1.1.1");
        instance.setPort(800);
        instance.setWeight(2);
        Map<String, String> map = new HashMap<String, String>();
        map.put("netType", "external");
        map.put("version", "2.0");
        instance.setMetadata(map);
    
        namingService.registerInstance("nacos.test.1", instance);
        verify(namingClientProxy, atLeastOnce()).registerService(any(String.class), any(String.class), any(Instance.class));
        
        List<Instance> list = namingService.getAllInstances("nacos.test.1", new ArrayList<String>(), false);
        verify(namingClientProxy, atLeastOnce()).queryInstancesOfService(any(String.class), any(String.class), any(String.class),any(Integer.class), any(Boolean.class));
    }
}
