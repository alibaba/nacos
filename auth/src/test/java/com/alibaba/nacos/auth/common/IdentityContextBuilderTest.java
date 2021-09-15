/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.auth.common;

import com.alibaba.nacos.api.remote.request.HealthCheckRequest;
import com.alibaba.nacos.auth.context.GrpcIdentityContextBuilder;
import com.alibaba.nacos.auth.context.HttpIdentityContextBuilder;
import com.alibaba.nacos.auth.context.IdentityContext;
import org.junit.Assert;
import org.junit.Test;
import com.alibaba.nacos.api.remote.request.Request;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.Vector;

@RunWith(MockitoJUnitRunner.class)
public class IdentityContextBuilderTest {
    
    String authority = "abc";
    
    String username = "aa";
    
    String password = "12345";
    
    String key = "abc";
    
    @Test
    public void testGrpcBuilt() {
        //before
        AuthConfigs authConfigs = Mockito.mock(AuthConfigs.class);
        Mockito.when(authConfigs.getAuthorityKey()).thenReturn(new String[] {"authority", "username", "password"});
        
        Request request = new HealthCheckRequest();
        request.putHeader("authority", authority);
        request.putHeader("username", username);
        request.putHeader("password", password);
        request.putHeader("key", key);
        //when
        GrpcIdentityContextBuilder grpcIdentityContextBuilder = new GrpcIdentityContextBuilder(authConfigs);
        IdentityContext identityContext = grpcIdentityContextBuilder.build(request);
        //then
        Assert.assertEquals(authority, identityContext.getParameter("authority"));
        Assert.assertEquals(username, identityContext.getParameter("username"));
        Assert.assertEquals(password, identityContext.getParameter("password"));
        Assert.assertNotEquals(key, identityContext.getParameter("key"));
    }
    
    @Test
    public void testHttpHeaderBuilt() {
        //before
        AuthConfigs authConfigs = Mockito.mock(AuthConfigs.class);
        Mockito.when(authConfigs.getAuthorityKey()).thenReturn(new String[] {"authority", "username", "password"});
        
        Vector<String> vector = new Vector<>();
        vector.add("username");
        vector.add("authority");
        vector.add("password");
        vector.add("key");
        Enumeration<String> enumeration = vector.elements();
        
        HttpServletRequest httpRequest = Mockito.mock(HttpServletRequest.class);
        Mockito.when(httpRequest.getHeaderNames()).thenReturn(enumeration);
        Mockito.when(httpRequest.getHeader("username")).thenReturn(username);
        Mockito.when(httpRequest.getHeader("password")).thenReturn(password);
        Mockito.when(httpRequest.getHeader("authority")).thenReturn(authority);
        Mockito.when(httpRequest.getHeader("key")).thenReturn(key);
        //when
        HttpIdentityContextBuilder httpIdentityContextBuilder = new HttpIdentityContextBuilder(authConfigs);
        IdentityContext identityContext = httpIdentityContextBuilder.build(httpRequest);
        //then
        Assert.assertEquals(authority, identityContext.getParameter("authority"));
        Assert.assertEquals(username, identityContext.getParameter("username"));
        Assert.assertEquals(password, identityContext.getParameter("password"));
        Assert.assertNotEquals(key, identityContext.getParameter("key"));
        
    }
    
    @Test
    public void testHttpParamBuilt() {
        //before
        AuthConfigs authConfigs = Mockito.mock(AuthConfigs.class);
        Mockito.when(authConfigs.getAuthorityKey()).thenReturn(new String[] {"authority", "username", "password"});
        
        Vector<String> vectorHeader = new Vector<>();
        vectorHeader.add("URL");
        Enumeration<String> enumerationHeader = vectorHeader.elements();
        
        HttpServletRequest httpRequest = Mockito.mock(HttpServletRequest.class);
        Mockito.when(httpRequest.getHeaderNames()).thenReturn(enumerationHeader);
    
        Vector<String> vectorParam = new Vector<>();
        vectorParam.add("username");
        vectorParam.add("authority");
        vectorParam.add("password");
        Enumeration<String> enumerationParam = vectorParam.elements();
        Mockito.when(httpRequest.getParameterNames()).thenReturn(enumerationParam);
        
        Mockito.when(httpRequest.getHeader("URL")).thenReturn("localhost");
        Mockito.when(httpRequest.getParameter("username")).thenReturn(username);
        Mockito.when(httpRequest.getParameter("password")).thenReturn(password);
        Mockito.when(httpRequest.getParameter("authority")).thenReturn(authority);
        Mockito.when(httpRequest.getParameter("key")).thenReturn(key);
        
        //when
        HttpIdentityContextBuilder httpIdentityContextBuilder = new HttpIdentityContextBuilder(authConfigs);
        IdentityContext identityContext = httpIdentityContextBuilder.build(httpRequest);
        //then
        Assert.assertEquals(authority, identityContext.getParameter("authority"));
        Assert.assertEquals(username, identityContext.getParameter("username"));
        Assert.assertEquals(password, identityContext.getParameter("password"));
        Assert.assertNotEquals(key, identityContext.getParameter("key"));
        
    }
    
    @Test
    public void testHttpHeaderParamBuilt() {
        //before
        AuthConfigs authConfigs = Mockito.mock(AuthConfigs.class);
        Mockito.when(authConfigs.getAuthorityKey()).thenReturn(new String[] {"authority", "username", "password"});
        
        Vector<String> vectorHeader = new Vector<>();
        vectorHeader.add("URL");
        vectorHeader.add("username");
        vectorHeader.add("password");
        Enumeration<String> enumerationHeader = vectorHeader.elements();
        
        HttpServletRequest httpRequest = Mockito.mock(HttpServletRequest.class);
        Mockito.when(httpRequest.getHeaderNames()).thenReturn(enumerationHeader);
        Mockito.when(httpRequest.getHeader("URL")).thenReturn("localhost");
        Mockito.when(httpRequest.getHeader("username")).thenReturn(username);
        Mockito.when(httpRequest.getHeader("password")).thenReturn("54321");
        
        Vector<String> vectorParam = new Vector<>();
        vectorParam.add("username");
        vectorParam.add("authority");
        vectorParam.add("password");
        Enumeration<String> enumerationParam = vectorParam.elements();
        
        Mockito.when(httpRequest.getParameterNames()).thenReturn(enumerationParam);
        Mockito.when(httpRequest.getParameter("password")).thenReturn(password);
        Mockito.when(httpRequest.getParameter("authority")).thenReturn(authority);
        Mockito.when(httpRequest.getParameter("key")).thenReturn(key);
        
        //when
        HttpIdentityContextBuilder httpIdentityContextBuilder = new HttpIdentityContextBuilder(authConfigs);
        IdentityContext identityContext = httpIdentityContextBuilder.build(httpRequest);
        //then
        Assert.assertEquals(authority, identityContext.getParameter("authority"));
        Assert.assertEquals(username, identityContext.getParameter("username"));
        Assert.assertNotEquals(password, identityContext.getParameter("password"));
        Assert.assertNotEquals(key, identityContext.getParameter("key"));
        
    }
    
}
