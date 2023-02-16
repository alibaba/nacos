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
import com.alibaba.nacos.client.auth.ram.utils.SignUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class NamingResourceInjectorTest {
    
    private NamingResourceInjector namingResourceInjector;
    
    private RamContext ramContext;
    
    private RequestResource resource;
    
    @Before
    public void setUp() throws Exception {
        namingResourceInjector = new NamingResourceInjector();
        ramContext = new RamContext();
        ramContext.setAccessKey(PropertyKeyConst.ACCESS_KEY);
        ramContext.setSecretKey(PropertyKeyConst.SECRET_KEY);
    }
    
    @Test
    public void testDoInjectWithGroup() throws Exception {
        resource = RequestResource.namingBuilder().setResource("test@@aaa").setGroup("group").build();
        LoginIdentityContext actual = new LoginIdentityContext();
        namingResourceInjector.doInject(resource, ramContext, actual);
        Assert.assertEquals(3, actual.getAllKey().size());
        Assert.assertEquals(PropertyKeyConst.ACCESS_KEY, actual.getParameter("ak"));
        Assert.assertTrue(actual.getParameter("data").endsWith("@@test@@aaa"));
        String expectSign = SignUtil.sign(actual.getParameter("data"), PropertyKeyConst.SECRET_KEY);
        Assert.assertEquals(expectSign, actual.getParameter("signature"));
    }
    
    @Test
    public void testDoInjectWithoutGroup() throws Exception {
        resource = RequestResource.namingBuilder().setResource("aaa").setGroup("group").build();
        LoginIdentityContext actual = new LoginIdentityContext();
        namingResourceInjector.doInject(resource, ramContext, actual);
        Assert.assertEquals(3, actual.getAllKey().size());
        Assert.assertEquals(PropertyKeyConst.ACCESS_KEY, actual.getParameter("ak"));
        Assert.assertTrue(actual.getParameter("data").endsWith("@@group@@aaa"));
        String expectSign = SignUtil.sign(actual.getParameter("data"), PropertyKeyConst.SECRET_KEY);
        Assert.assertEquals(expectSign, actual.getParameter("signature"));
    }
}
