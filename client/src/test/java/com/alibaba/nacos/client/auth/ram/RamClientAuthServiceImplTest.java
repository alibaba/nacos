/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.auth.ram;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.auth.ram.injector.AbstractResourceInjector;
import com.alibaba.nacos.common.utils.ReflectUtils;
import com.alibaba.nacos.plugin.auth.api.LoginIdentityContext;
import com.alibaba.nacos.plugin.auth.api.RequestResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RamClientAuthServiceImplTest {
    
    private static final String MOCK = "mock";
    
    @Mock
    private AbstractResourceInjector mockResourceInjector;
    
    private RamClientAuthServiceImpl ramClientAuthService;
    
    private Properties akSkProperties;
    
    private Properties roleProperties;
    
    private RamContext ramContext;
    
    private RequestResource resource;
    
    @BeforeEach
    void setUp() throws Exception {
        ramClientAuthService = new RamClientAuthServiceImpl();
        Map<String, AbstractResourceInjector> resourceInjectors = (Map<String, AbstractResourceInjector>) ReflectUtils.getFieldValue(
                ramClientAuthService, "resourceInjectors");
        resourceInjectors.clear();
        resourceInjectors.put(MOCK, mockResourceInjector);
        ramContext = (RamContext) ReflectUtils.getFieldValue(ramClientAuthService, "ramContext");
        akSkProperties = new Properties();
        roleProperties = new Properties();
        akSkProperties.setProperty(PropertyKeyConst.ACCESS_KEY, PropertyKeyConst.ACCESS_KEY);
        akSkProperties.setProperty(PropertyKeyConst.SECRET_KEY, PropertyKeyConst.SECRET_KEY);
        roleProperties.setProperty(PropertyKeyConst.RAM_ROLE_NAME, PropertyKeyConst.RAM_ROLE_NAME);
        resource = new RequestResource();
    }
    
    @AfterEach
    void tearDown() throws NacosException {
        ramClientAuthService.shutdown();
    }
    
    @Test
    void testLoginWithAkSk() {
        assertTrue(ramClientAuthService.login(akSkProperties));
        assertEquals(PropertyKeyConst.ACCESS_KEY, ramContext.getAccessKey());
        assertEquals(PropertyKeyConst.SECRET_KEY, ramContext.getSecretKey());
        assertNull(ramContext.getRamRoleName());
        assertTrue(ramClientAuthService.login(roleProperties));
        assertEquals(PropertyKeyConst.ACCESS_KEY, ramContext.getAccessKey());
        assertEquals(PropertyKeyConst.SECRET_KEY, ramContext.getSecretKey());
        assertNull(ramContext.getRamRoleName());
    }
    
    @Test
    void testLoginWithRoleName() {
        assertTrue(ramClientAuthService.login(roleProperties));
        assertNull(ramContext.getAccessKey(), PropertyKeyConst.ACCESS_KEY);
        assertNull(ramContext.getSecretKey(), PropertyKeyConst.SECRET_KEY);
        assertEquals(PropertyKeyConst.RAM_ROLE_NAME, ramContext.getRamRoleName());
        assertTrue(ramClientAuthService.login(akSkProperties));
        assertNull(ramContext.getAccessKey(), PropertyKeyConst.ACCESS_KEY);
        assertNull(ramContext.getSecretKey(), PropertyKeyConst.SECRET_KEY);
        assertEquals(PropertyKeyConst.RAM_ROLE_NAME, ramContext.getRamRoleName());
    }
    
    @Test
    void testGetLoginIdentityContextWithoutLogin() {
        LoginIdentityContext actual = ramClientAuthService.getLoginIdentityContext(resource);
        assertTrue(actual.getAllKey().isEmpty());
        verify(mockResourceInjector, never()).doInject(resource, ramContext, actual);
    }
    
    @Test
    void testGetLoginIdentityContextWithoutInjector() {
        ramClientAuthService.login(akSkProperties);
        LoginIdentityContext actual = ramClientAuthService.getLoginIdentityContext(resource);
        assertTrue(actual.getAllKey().isEmpty());
        verify(mockResourceInjector, never()).doInject(resource, ramContext, actual);
    }
    
    @Test
    void testGetLoginIdentityContextWithInjector() {
        ramClientAuthService.login(akSkProperties);
        resource.setType(MOCK);
        LoginIdentityContext actual = ramClientAuthService.getLoginIdentityContext(resource);
        assertTrue(actual.getAllKey().isEmpty());
        verify(mockResourceInjector).doInject(resource, ramContext, actual);
    }
}
