/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.http;

import com.alibaba.nacos.common.http.client.NacosAsyncRestTemplate;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.tls.TlsSystemConfig;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;

import static org.junit.Assert.assertNotNull;

@RunWith(MockitoJUnitRunner.class)
public class AbstractHttpClientFactoryTest {
    
    @Mock
    private Logger logger;
    
    @After
    public void tearDown() throws Exception {
        TlsSystemConfig.tlsEnable = false;
    }
    
    @Test
    public void testCreateNacosRestTemplateWithSsl() throws Exception {
        TlsSystemConfig.tlsEnable = true;
        HttpClientFactory httpClientFactory = new DefaultHttpClientFactory(logger);
        NacosRestTemplate nacosRestTemplate = httpClientFactory.createNacosRestTemplate();
        assertNotNull(nacosRestTemplate);
    }
    
    @Test
    public void testCreateNacosAsyncRestTemplate() {
        HttpClientFactory httpClientFactory = new AbstractHttpClientFactory() {
            @Override
            protected HttpClientConfig buildHttpClientConfig() {
                return HttpClientConfig.builder().setMaxConnTotal(10).setMaxConnPerRoute(10).build();
            }
            
            @Override
            protected Logger assignLogger() {
                return logger;
            }
        };
        NacosAsyncRestTemplate nacosRestTemplate = httpClientFactory.createNacosAsyncRestTemplate();
        assertNotNull(nacosRestTemplate);
    }
}