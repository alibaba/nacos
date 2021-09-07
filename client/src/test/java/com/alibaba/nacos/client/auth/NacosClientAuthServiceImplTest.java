package com.alibaba.nacos.client.auth;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.client.auth.result.ResultConstant;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import org.junit.Assert;
import org.junit.Test;

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
        NacosClientAuthServiceImpl securityProxy = new NacosClientAuthServiceImpl(properties, nacosRestTemplate);
        properties.setProperty(ResultConstant.SERVER, "localhost");
        //when
        boolean ret = securityProxy.login(properties);
        //then
        Assert.assertTrue(ret);
    }
}
