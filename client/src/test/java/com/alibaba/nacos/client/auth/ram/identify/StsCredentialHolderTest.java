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
import com.alibaba.nacos.common.http.HttpClientBeanHolder;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StsCredentialHolderTest {
    
    private String securityCredentialsUrl;
    
    private NacosRestTemplate cachedNacosRestTemplate;
    
    @Mock
    private HttpRestResult mockResult;
    
    @Mock
    private NacosRestTemplate nacosRestTemplate;
    
    @BeforeEach
    void setUp() throws Exception {
        securityCredentialsUrl = StsConfig.getInstance().getSecurityCredentialsUrl();
        StsConfig.getInstance().setSecurityCredentialsUrl("url");
        Field restMapField = HttpClientBeanHolder.class.getDeclaredField("SINGLETON_REST");
        restMapField.setAccessible(true);
        Map<String, NacosRestTemplate> restMap = (Map<String, NacosRestTemplate>) restMapField.get(null);
        cachedNacosRestTemplate = restMap.get(
                "com.alibaba.nacos.client.config.impl.ConfigHttpClientManager$ConfigHttpClientFactory");
        restMap.put("com.alibaba.nacos.client.config.impl.ConfigHttpClientManager$ConfigHttpClientFactory", nacosRestTemplate);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        StsConfig.getInstance().setSecurityCredentials(null);
        StsConfig.getInstance().setSecurityCredentialsUrl(securityCredentialsUrl);
        if (null != cachedNacosRestTemplate) {
            Field restMapField = HttpClientBeanHolder.class.getDeclaredField("SINGLETON_REST");
            restMapField.setAccessible(true);
            Map<String, NacosRestTemplate> restMap = (Map<String, NacosRestTemplate>) restMapField.get(null);
            restMap.put("com.alibaba.nacos.client.config.impl.ConfigHttpClientManager$ConfigHttpClientFactory",
                    cachedNacosRestTemplate);
        }
        clearForSts();
    }
    
    private void clearForSts() throws NoSuchFieldException, IllegalAccessException {
        StsConfig.getInstance().setSecurityCredentialsUrl(null);
        Field field = StsCredentialHolder.class.getDeclaredField("stsCredential");
        field.setAccessible(true);
        field.set(StsCredentialHolder.getInstance(), null);
    }
    
    @Test
    void testGetStsCredentialFromCache() throws NoSuchFieldException, IllegalAccessException {
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
    void testGetStsCredentialFromStringCache() throws NoSuchFieldException, IllegalAccessException {
        StsCredential stsCredential = buildMockStsCredential();
        StsConfig.getInstance().setSecurityCredentials(JacksonUtils.toJson(stsCredential));
        assertEquals(stsCredential.toString(), StsCredentialHolder.getInstance().getStsCredential().toString());
    }
    
    @Test
    void testGetStsCredentialFromRequest() throws Exception {
        StsCredential stsCredential = buildMockStsCredential();
        mockResult = new HttpRestResult<String>();
        mockResult.setData(JacksonUtils.toJson(stsCredential));
        mockResult.setCode(200);
        when(nacosRestTemplate.get(any(), any(), any(), any())).thenReturn(mockResult);
        assertEquals(stsCredential.toString(), StsCredentialHolder.getInstance().getStsCredential().toString());
    }
    
    @Test
    void testGetStsCredentialFromRequestFailure() throws Exception {
        assertThrows(NacosRuntimeException.class, () -> {
            mockResult = new HttpRestResult<String>();
            mockResult.setData("");
            mockResult.setCode(500);
            when(nacosRestTemplate.get(any(), any(), any(), any())).thenReturn(mockResult);
            StsCredentialHolder.getInstance().getStsCredential();
        });
    }
    
    @Test
    void testGetStsCredentialFromRequestException() throws Exception {
        assertThrows(NacosRuntimeException.class, () -> {
            when(nacosRestTemplate.get(any(), any(), any(), any())).thenThrow(new RuntimeException("test"));
            StsCredentialHolder.getInstance().getStsCredential();
        });
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