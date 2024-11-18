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

package com.alibaba.nacos.naming;

import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.push.UdpPushService;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.lang.reflect.Field;

import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
public abstract class BaseTest {
    
    protected static final String TEST_CLUSTER_NAME = "test-cluster";
    
    protected static final String TEST_SERVICE_NAME = "DEFAULT_GROUP@@test-service";
    
    protected static final String TEST_GROUP_NAME = "test-group-name";
    
    protected static final String TEST_NAMESPACE = "test-namespace";
    
    protected static final String TEST_IP = "1.1.1.1";
    
    protected static final String TEST_METADATA = "{\"label\":\"123\"}";
    
    protected static final String TEST_INSTANCE_INFO_LIST = "[{\"instanceId\":\"123\",\"ip\":\"1.1.1.1\","
            + "\"port\":9870,\"weight\":2.0,\"healthy\":true,\"enabled\":true,\"ephemeral\":true"
            + ",\"clusterName\":\"clusterName\",\"serviceName\":\"serviceName\",\"metadata\":{}}]";
    
    @Spy
    protected ConfigurableApplicationContext context;
    
    @Mock
    protected DistroMapper distroMapper;
    
    @Spy
    protected SwitchDomain switchDomain;
    
    @Mock
    protected UdpPushService pushService;
    
    @Spy
    protected MockEnvironment environment;
    
    @BeforeEach
    public void before() {
        EnvUtil.setEnvironment(environment);
        ApplicationUtils.injectContext(context);
    }
    
    protected MockHttpServletRequestBuilder convert(Object simpleOb, MockHttpServletRequestBuilder builder) throws IllegalAccessException {
        Field[] declaredFields = simpleOb.getClass().getDeclaredFields();
        for (Field declaredField : declaredFields) {
            declaredField.setAccessible(true);
            builder.param(declaredField.getName(), String.valueOf(declaredField.get(simpleOb)));
        }
        return builder;
    }
    
    protected void mockInjectPushServer() {
        doReturn(pushService).when(context).getBean(UdpPushService.class);
    }
    
    protected void mockInjectSwitchDomain() {
        doReturn(switchDomain).when(context).getBean(SwitchDomain.class);
    }
    
    protected void mockInjectDistroMapper() {
        doReturn(distroMapper).when(context).getBean(DistroMapper.class);
    }
}
