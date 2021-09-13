package com.alibaba.nacos.client.auth;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NacosClientAuthServiceImplTest {
    
    @Test
    public void testLoginSuccess() throws Exception {
        //given
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        HttpRestResult<Object> result = new HttpRestResult<>();
        result.setData("{\"accessToken\":\"ttttttttttttttttt\",\"tokenTtl\":1000}");
        result.setCode(200);
        when(nacosRestTemplate.postForm(any(), (Header) any(), any(), any(), any())).thenReturn(result);
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.USERNAME, "aaa");
        properties.setProperty(PropertyKeyConst.PASSWORD, "123456");
        List<String> serverList = new ArrayList<>();
        serverList.add("localhost");
        
        NacosClientAuthServiceImpl nacosClientAuthService = new NacosClientAuthServiceImpl();
        nacosClientAuthService.setServerList(serverList);
        nacosClientAuthService.setNacosRestTemplate(nacosRestTemplate);
        //when
        boolean ret = nacosClientAuthService.login(properties);
        //then
        Assert.assertTrue(ret);
    }
    
    @Test
    public void testTestLoginFailCode() throws Exception {
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        HttpRestResult<Object> result = new HttpRestResult<>();
        result.setCode(400);
        when(nacosRestTemplate.postForm(any(), (Header) any(), any(), any(), any())).thenReturn(result);
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.USERNAME, "aaa");
        properties.setProperty(PropertyKeyConst.PASSWORD, "123456");
        List<String> serverList = new ArrayList<>();
        serverList.add("localhost");
    
        NacosClientAuthServiceImpl nacosClientAuthService = new NacosClientAuthServiceImpl();
        nacosClientAuthService.setServerList(serverList);
        nacosClientAuthService.setNacosRestTemplate(nacosRestTemplate);
        boolean ret = nacosClientAuthService.login(properties);
        Assert.assertFalse(ret);
    }
    
    @Test
    public void testTestLoginFailHttp() throws Exception {
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        when(nacosRestTemplate.postForm(any(), (Header) any(), any(), any(), any())).thenThrow(new Exception());
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.USERNAME, "aaa");
        properties.setProperty(PropertyKeyConst.PASSWORD, "123456");
        List<String> serverList = new ArrayList<>();
        serverList.add("localhost");
    
        NacosClientAuthServiceImpl nacosClientAuthService = new NacosClientAuthServiceImpl();
        nacosClientAuthService.setServerList(serverList);
        nacosClientAuthService.setNacosRestTemplate(nacosRestTemplate);
        boolean ret = nacosClientAuthService.login(properties);
        Assert.assertFalse(ret);
        
    }
    
    @Test
    public void testTestLoginServerListSuccess() throws Exception {
        //given
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        HttpRestResult<Object> result = new HttpRestResult<>();
        result.setData("{\"accessToken\":\"ttttttttttttttttt\",\"tokenTtl\":1000}");
        result.setCode(200);
        when(nacosRestTemplate.postForm(any(), (Header) any(), any(), any(), any())).thenReturn(result);
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.USERNAME, "aaa");
        properties.setProperty(PropertyKeyConst.PASSWORD, "123456");
        List<String> serverList = new ArrayList<>();
        serverList.add("localhost");
        serverList.add("localhost");
    
        NacosClientAuthServiceImpl nacosClientAuthService = new NacosClientAuthServiceImpl();
        nacosClientAuthService.setServerList(serverList);
        nacosClientAuthService.setNacosRestTemplate(nacosRestTemplate);
        boolean ret = nacosClientAuthService.login(properties);
        Assert.assertTrue(ret);
    }
    
    @Test
    public void testTestLoginServerListLoginInWindow() throws Exception {
        //given
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        HttpRestResult<Object> result = new HttpRestResult<>();
        result.setData("{\"accessToken\":\"ttttttttttttttttt\",\"tokenTtl\":1000}");
        result.setCode(200);
        when(nacosRestTemplate.postForm(any(), (Header) any(), any(), any(), any())).thenReturn(result);
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.USERNAME, "aaa");
        properties.setProperty(PropertyKeyConst.PASSWORD, "123456");
        List<String> serverList = new ArrayList<>();
        serverList.add("localhost");
        
        NacosClientAuthServiceImpl nacosClientAuthService = new NacosClientAuthServiceImpl();
        nacosClientAuthService.setServerList(serverList);
        nacosClientAuthService.setNacosRestTemplate(nacosRestTemplate);
        //when
        nacosClientAuthService.login(properties);
        //then
        boolean ret = nacosClientAuthService.login(properties);
        Assert.assertTrue(ret);
        
    }
   
    @Test
    public void testGetAccessToken() throws Exception {
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        HttpRestResult<Object> result = new HttpRestResult<>();
        result.setData("{\"accessToken\":\"abc\",\"tokenTtl\":1000}");
        result.setCode(200);
        when(nacosRestTemplate.postForm(any(), (Header) any(), any(), any(), any())).thenReturn(result);
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.USERNAME, "aaa");
        properties.setProperty(PropertyKeyConst.PASSWORD, "123456");
    
        List<String> serverList = new ArrayList<>();
        serverList.add("localhost");
    
        NacosClientAuthServiceImpl nacosClientAuthService = new NacosClientAuthServiceImpl();
        nacosClientAuthService.setServerList(serverList);
        nacosClientAuthService.setNacosRestTemplate(nacosRestTemplate);
        //when
        Assert.assertTrue(nacosClientAuthService.login(properties));
        //then
        Assert.assertEquals("abc", nacosClientAuthService.getLoginIdentityContext().getParameter(LoginAuthConstant.ACCESSTOKEN));
    }
    
}
