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

package com.alibaba.nacos.client.auth.ram.identify;

import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.client.config.impl.ConfigHttpClientManager;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.client.request.HttpClientRequest;
import com.alibaba.nacos.common.http.client.response.HttpClientResponse;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class StsCredentialHolderTest {
    
    private String securityCredentialsUrl;
    
    private HttpClientRequest httpClient;
    
    @Mock
    private HttpClientRequest mockRest;
    
    @Before
    public void setUp() throws Exception {
        securityCredentialsUrl = StsConfig.getInstance().getSecurityCredentialsUrl();
        StsConfig.getInstance().setSecurityCredentialsUrl("url");
        Field field = NacosRestTemplate.class.getDeclaredField("requestClient");
        field.setAccessible(true);
        httpClient = (HttpClientRequest) field.get(ConfigHttpClientManager.getInstance().getNacosRestTemplate());
        field.set(ConfigHttpClientManager.getInstance().getNacosRestTemplate(), mockRest);
    }
    
    @After
    public void tearDown() throws Exception {
        StsConfig.getInstance().setSecurityCredentials(null);
        StsConfig.getInstance().setSecurityCredentialsUrl(securityCredentialsUrl);
        Field field = NacosRestTemplate.class.getDeclaredField("requestClient");
        field.setAccessible(true);
        field.set(ConfigHttpClientManager.getInstance().getNacosRestTemplate(), httpClient);
        clearForSts();
    }
    
    private void clearForSts() throws NoSuchFieldException, IllegalAccessException {
        StsConfig.getInstance().setSecurityCredentialsUrl(null);
        Field field = StsCredentialHolder.class.getDeclaredField("stsCredential");
        field.setAccessible(true);
        field.set(StsCredentialHolder.getInstance(), null);
    }
    
    @Test
    public void testGetStsCredentialFromCache() throws NoSuchFieldException, IllegalAccessException {
        StsCredential stsCredential = buildMockStsCredential();
        setStsCredential(stsCredential);
        assertEquals(stsCredential, StsCredentialHolder.getInstance().getStsCredential());
    }
    
    private void setStsCredential(StsCredential stsCredential) throws NoSuchFieldException, IllegalAccessException {
        Field field = StsCredentialHolder.class.getDeclaredField("stsCredential");
        field.setAccessible(true);
        field.set(StsCredentialHolder.getInstance(), stsCredential);
    }
    
    @Test
    public void testGetStsCredentialFromStringCache() throws NoSuchFieldException, IllegalAccessException {
        StsCredential stsCredential = buildMockStsCredential();
        StsConfig.getInstance().setSecurityCredentials(JacksonUtils.toJson(stsCredential));
        assertEquals(stsCredential.toString(), StsCredentialHolder.getInstance().getStsCredential().toString());
    }
    
    @Test
    public void testGetStsCredentialFromRequest() throws Exception {
        StsCredential stsCredential = buildMockStsCredential();
        HttpClientResponse response = mock(HttpClientResponse.class);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getHeaders()).thenReturn(Header.newInstance());
        when(response.getBody()).thenReturn(new ByteArrayInputStream(JacksonUtils.toJsonBytes(stsCredential)));
        when(mockRest.execute(any(), any(), any())).thenReturn(response);
        assertEquals(stsCredential.toString(), StsCredentialHolder.getInstance().getStsCredential().toString());
    }
    
    @Test(expected = NacosRuntimeException.class)
    public void testGetStsCredentialFromRequestFailure() throws Exception {
        HttpClientResponse response = mock(HttpClientResponse.class);
        when(response.getStatusCode()).thenReturn(500);
        when(response.getHeaders()).thenReturn(Header.newInstance());
        when(response.getBody()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(mockRest.execute(any(), any(), any())).thenReturn(response);
        StsCredentialHolder.getInstance().getStsCredential();
    }
    
    @Test(expected = NacosRuntimeException.class)
    public void testGetStsCredentialFromRequestException() throws Exception {
        when(mockRest.execute(any(), any(), any())).thenThrow(new RuntimeException("test"));
        StsCredentialHolder.getInstance().getStsCredential();
    }
    
    private StsCredential buildMockStsCredential() {
        StsCredential stsCredential = new StsCredential();
        stsCredential.setAccessKeyId("test-sts-ak");
        stsCredential.setAccessKeySecret("test-sts-sk");
        stsCredential.setSecurityToken("test-sts-token");
        stsCredential.setExpiration(new Date(System.currentTimeMillis() + 1000000));
        stsCredential.setCode("200");
        stsCredential.setLastUpdated(new Date());
        return stsCredential;
    }
}