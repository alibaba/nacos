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

package com.alibaba.nacos.client.security;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.client.auth.impl.NacosAuthLoginConstant;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SecurityProxyTest {
    
    private SecurityProxy securityProxy;
    
    @Before
    public void setUp() throws Exception {
        //given
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        HttpRestResult<Object> result = new HttpRestResult<>();
        result.setData("{\"accessToken\":\"ttttttttttttttttt\",\"tokenTtl\":1000}");
        result.setCode(200);
        when(nacosRestTemplate.postForm(any(), (Header) any(), any(), any(), any())).thenReturn(result);
        
        List<String> serverList = new ArrayList<>();
        serverList.add("localhost");
        securityProxy = new SecurityProxy(serverList, nacosRestTemplate);
    }
    
    @Test
    public void testLoginClientAuthService() {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.USERNAME, "aaa");
        properties.setProperty(PropertyKeyConst.PASSWORD, "123456");
        //when
        boolean ret = securityProxy.login(properties);
        //then
        Assert.assertTrue(ret);
    }
    
    @Test
    public void testGetIdentityContext() {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.USERNAME, "aaa");
        properties.setProperty(PropertyKeyConst.PASSWORD, "123456");
        securityProxy.login(properties);
        //when
        Map<String, String> keyMap = securityProxy.getIdentityContext();
        //then
        Assert.assertEquals("ttttttttttttttttt", keyMap.get(NacosAuthLoginConstant.ACCESSTOKEN));
    }
    
}
