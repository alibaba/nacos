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

package com.alibaba.nacos.client.auth.ram.injector;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.plugin.auth.api.LoginIdentityContext;
import com.alibaba.nacos.client.auth.ram.RamContext;
import com.alibaba.nacos.plugin.auth.api.RequestResource;
import com.alibaba.nacos.client.auth.ram.identify.StsConfig;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ConfigResourceInjectorTest {
    
    private ConfigResourceInjector configResourceInjector;
    
    private RamContext ramContext;
    
    private RequestResource resource;
    
    private String cachedSecurityCredentialsUrl;
    
    private String cachedSecurityCredentials;
    
    @Before
    public void setUp() throws Exception {
        configResourceInjector = new ConfigResourceInjector();
        ramContext = new RamContext();
        ramContext.setAccessKey(PropertyKeyConst.ACCESS_KEY);
        ramContext.setSecretKey(PropertyKeyConst.SECRET_KEY);
        resource = new RequestResource();
        resource.setType(SignType.CONFIG);
        resource.setNamespace("tenant");
        resource.setGroup("group");
        cachedSecurityCredentialsUrl = StsConfig.getInstance().getSecurityCredentialsUrl();
        cachedSecurityCredentials = StsConfig.getInstance().getSecurityCredentials();
        StsConfig.getInstance().setSecurityCredentialsUrl("");
        StsConfig.getInstance().setSecurityCredentials("");
    }
    
    @After
    public void tearDown() {
        StsConfig.getInstance().setSecurityCredentialsUrl(cachedSecurityCredentialsUrl);
        StsConfig.getInstance().setSecurityCredentials(cachedSecurityCredentials);
    }
    
    @Test
    public void testDoInject() throws Exception {
        LoginIdentityContext actual = new LoginIdentityContext();
        configResourceInjector.doInject(resource, ramContext, actual);
        Assert.assertEquals(3, actual.getAllKey().size());
        Assert.assertEquals(PropertyKeyConst.ACCESS_KEY, actual.getParameter("Spas-AccessKey"));
        Assert.assertTrue(actual.getAllKey().contains("Timestamp"));
        Assert.assertTrue(actual.getAllKey().contains("Spas-Signature"));
    }
}
