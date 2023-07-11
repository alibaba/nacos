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
import com.alibaba.nacos.client.auth.ram.RamContext;
import com.alibaba.nacos.client.auth.ram.identify.StsConfig;
import com.alibaba.nacos.client.auth.ram.identify.StsCredential;
import com.alibaba.nacos.client.auth.ram.identify.StsCredentialHolder;
import com.alibaba.nacos.client.auth.ram.utils.SignUtil;
import com.alibaba.nacos.plugin.auth.api.LoginIdentityContext;
import com.alibaba.nacos.plugin.auth.api.RequestResource;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Date;

public class NamingResourceInjectorTest {
    
    private NamingResourceInjector namingResourceInjector;
    
    private RamContext ramContext;
    
    private RequestResource resource;
    
    private StsCredential stsCredential;
    
    @Before
    public void setUp() throws Exception {
        namingResourceInjector = new NamingResourceInjector();
        ramContext = new RamContext();
        ramContext.setAccessKey(PropertyKeyConst.ACCESS_KEY);
        ramContext.setSecretKey(PropertyKeyConst.SECRET_KEY);
        stsCredential = new StsCredential();
        StsConfig.getInstance().setRamRoleName(null);
    }
    
    @After
    public void tearDown() throws NoSuchFieldException, IllegalAccessException {
        clearForSts();
    }
    
    @Test
    public void testDoInjectWithEmpty() throws Exception {
        resource = RequestResource.namingBuilder().setResource("").build();
        LoginIdentityContext actual = new LoginIdentityContext();
        namingResourceInjector.doInject(resource, ramContext, actual);
        String expectSign = SignUtil.sign(actual.getParameter("data"), PropertyKeyConst.SECRET_KEY);
        Assert.assertEquals(3, actual.getAllKey().size());
        Assert.assertEquals(PropertyKeyConst.ACCESS_KEY, actual.getParameter("ak"));
        Assert.assertTrue(Long.parseLong(actual.getParameter("data")) - System.currentTimeMillis() < 100);
        Assert.assertEquals(expectSign, actual.getParameter("signature"));
    }
    
    @Test
    public void testDoInjectWithGroup() throws Exception {
        resource = RequestResource.namingBuilder().setResource("test@@aaa").setGroup("group").build();
        LoginIdentityContext actual = new LoginIdentityContext();
        namingResourceInjector.doInject(resource, ramContext, actual);
        String expectSign = SignUtil.sign(actual.getParameter("data"), PropertyKeyConst.SECRET_KEY);
        Assert.assertEquals(3, actual.getAllKey().size());
        Assert.assertEquals(PropertyKeyConst.ACCESS_KEY, actual.getParameter("ak"));
        Assert.assertTrue(actual.getParameter("data").endsWith("@@test@@aaa"));
        Assert.assertEquals(expectSign, actual.getParameter("signature"));
    }
    
    @Test
    public void testDoInjectWithoutGroup() throws Exception {
        resource = RequestResource.namingBuilder().setResource("aaa").setGroup("group").build();
        LoginIdentityContext actual = new LoginIdentityContext();
        namingResourceInjector.doInject(resource, ramContext, actual);
        Assert.assertTrue(actual.getParameter("data").endsWith("@@group@@aaa"));
        Assert.assertEquals(3, actual.getAllKey().size());
        Assert.assertEquals(PropertyKeyConst.ACCESS_KEY, actual.getParameter("ak"));
        String expectSign = SignUtil.sign(actual.getParameter("data"), PropertyKeyConst.SECRET_KEY);
        Assert.assertEquals(expectSign, actual.getParameter("signature"));
    }
    
    @Test
    public void testDoInjectWithGroupForSts() throws Exception {
        prepareForSts();
        resource = RequestResource.namingBuilder().setResource("test@@aaa").setGroup("group").build();
        LoginIdentityContext actual = new LoginIdentityContext();
        namingResourceInjector.doInject(resource, ramContext, actual);
        String expectSign = SignUtil.sign(actual.getParameter("data"), "test-sts-sk");
        Assert.assertEquals(4, actual.getAllKey().size());
        Assert.assertEquals("test-sts-ak", actual.getParameter("ak"));
        Assert.assertTrue(actual.getParameter("data").endsWith("@@test@@aaa"));
        Assert.assertEquals(expectSign, actual.getParameter("signature"));
    }
    
    @Test
    public void testDoInjectWithoutGroupForSts() throws Exception {
        prepareForSts();
        resource = RequestResource.namingBuilder().setResource("aaa").setGroup("group").build();
        LoginIdentityContext actual = new LoginIdentityContext();
        namingResourceInjector.doInject(resource, ramContext, actual);
        String expectSign = SignUtil.sign(actual.getParameter("data"), "test-sts-sk");
        Assert.assertEquals(4, actual.getAllKey().size());
        Assert.assertEquals("test-sts-ak", actual.getParameter("ak"));
        Assert.assertTrue(actual.getParameter("data").endsWith("@@group@@aaa"));
        Assert.assertEquals(expectSign, actual.getParameter("signature"));
    }
    
    @Test
    public void testDoInjectForStsWithException() throws Exception {
        prepareForSts();
        stsCredential.setExpiration(new Date());
        resource = RequestResource.namingBuilder().setResource("aaa").setGroup("group").build();
        LoginIdentityContext actual = new LoginIdentityContext();
        namingResourceInjector.doInject(resource, ramContext, actual);
        Assert.assertEquals(0, actual.getAllKey().size());
    }
    
    private void prepareForSts() throws NoSuchFieldException, IllegalAccessException {
        StsConfig.getInstance().setSecurityCredentialsUrl("test");
        Field field = StsCredentialHolder.class.getDeclaredField("stsCredential");
        field.setAccessible(true);
        field.set(StsCredentialHolder.getInstance(), stsCredential);
        stsCredential.setAccessKeyId("test-sts-ak");
        stsCredential.setAccessKeySecret("test-sts-sk");
        stsCredential.setSecurityToken("test-sts-token");
        stsCredential.setExpiration(new Date(System.currentTimeMillis() + 1000000));
    }
    
    private void clearForSts() throws NoSuchFieldException, IllegalAccessException {
        StsConfig.getInstance().setSecurityCredentialsUrl(null);
        StsConfig.getInstance().setSecurityCredentials(null);
        Field field = StsCredentialHolder.class.getDeclaredField("stsCredential");
        field.setAccessible(true);
        field.set(StsCredentialHolder.getInstance(), null);
    }
}
