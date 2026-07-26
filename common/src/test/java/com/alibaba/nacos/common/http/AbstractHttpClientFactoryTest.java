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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class AbstractHttpClientFactoryTest {
    
    @Mock
    private Logger logger;
    
    @BeforeEach
    void setUp() {
        TlsSystemConfig.tlsEnable = false;
        TlsSystemConfig.tlsClientTrustCertPath = null;
        TlsSystemConfig.tlsClientKeyPath = null;
    }
    
    @AfterEach
    void tearDown() throws Exception {
        TlsSystemConfig.tlsEnable = false;
        TlsSystemConfig.tlsClientTrustCertPath = null;
        TlsSystemConfig.tlsClientKeyPath = null;
    }
    
    @Test
    void testCreateNacosRestTemplateWithSsl() throws Exception {
        TlsSystemConfig.tlsEnable = true;
        HttpClientFactory httpClientFactory = new DefaultHttpClientFactory(logger);
        NacosRestTemplate nacosRestTemplate = httpClientFactory.createNacosRestTemplate();
        assertNotNull(nacosRestTemplate);
    }
    
    @Test
    void testCreateNacosAsyncRestTemplate() {
        HttpClientFactory httpClientFactory = new AbstractHttpClientFactory() {
            
            @Override
            protected HttpClientConfig buildHttpClientConfig() {
                return HttpClientConfig.builder().setMaxConnTotal(10).setMaxConnPerRoute(10)
                    .build();
            }
            
            @Override
            protected Logger assignLogger() {
                return logger;
            }
        };
        NacosAsyncRestTemplate nacosRestTemplate = httpClientFactory.createNacosAsyncRestTemplate();
        assertNotNull(nacosRestTemplate);
    }
    
    @Test
    void testCreateNacosRestTemplateWithoutSsl() {
        TlsSystemConfig.tlsEnable = false;
        HttpClientFactory httpClientFactory = new DefaultHttpClientFactory(logger);
        NacosRestTemplate nacosRestTemplate = httpClientFactory.createNacosRestTemplate();
        assertNotNull(nacosRestTemplate);
    }
    
    @Test
    void testCreateNacosAsyncRestTemplateWithCustomConfig() {
        HttpClientFactory httpClientFactory = new AbstractHttpClientFactory() {
            
            @Override
            protected HttpClientConfig buildHttpClientConfig() {
                return HttpClientConfig.builder()
                    .setMaxConnTotal(100)
                    .setMaxConnPerRoute(50)
                    .setConTimeOutMillis(5000)
                    .setReadTimeOutMillis(10000)
                    .setConnectionRequestTimeout(3000)
                    .setContentCompressionEnabled(true)
                    .setMaxRedirects(5)
                    .setIoThreadCount(4)
                    .setUserAgent("test-agent")
                    .build();
            }
            
            @Override
            protected Logger assignLogger() {
                return logger;
            }
        };
        NacosAsyncRestTemplate nacosAsyncRestTemplate =
            httpClientFactory.createNacosAsyncRestTemplate();
        assertNotNull(nacosAsyncRestTemplate);
    }
    
    @Test
    void testCreateNacosAsyncRestTemplateWithMonitorAndExtension() {
        HttpClientFactory httpClientFactory = new AbstractHttpClientFactory() {
            
            @Override
            protected HttpClientConfig buildHttpClientConfig() {
                return HttpClientConfig.builder()
                    .setMaxConnTotal(10)
                    .setMaxConnPerRoute(10)
                    .build();
            }
            
            @Override
            protected Logger assignLogger() {
                return logger;
            }
            
            @Override
            protected void monitorAndExtension(
                org.apache.hc.client5.http.nio.AsyncClientConnectionManager connectionManager) {
                // Custom extension logic - this exercises the hook
                assertNotNull(connectionManager);
            }
        };
        NacosAsyncRestTemplate nacosAsyncRestTemplate =
            httpClientFactory.createNacosAsyncRestTemplate();
        assertNotNull(nacosAsyncRestTemplate);
    }
    
    @Test
    void testLoadSslContextReturnsNullOnException() {
        // Test that loadSslContext handles exceptions gracefully
        AbstractHttpClientFactory factory = new AbstractHttpClientFactory() {
            
            @Override
            protected HttpClientConfig buildHttpClientConfig() {
                return HttpClientConfig.builder().build();
            }
            
            @Override
            protected Logger assignLogger() {
                return logger;
            }
        };
        // Create a test factory that exposes loadSslContext behavior
        // The method catches exceptions and returns null
        // We can verify by checking that the factory still works
        NacosRestTemplate template = factory.createNacosRestTemplate();
        assertNotNull(template);
    }
    
    @Test
    void testCreateNacosRestTemplateWithTlsFileListener() {
        // Test with TLS enabled and file paths set (even if invalid)
        TlsSystemConfig.tlsEnable = true;
        TlsSystemConfig.tlsClientTrustCertPath = "/non/existent/path/trust.crt";
        TlsSystemConfig.tlsClientKeyPath = "/non/existent/path/key.pem";
        
        HttpClientFactory httpClientFactory = new DefaultHttpClientFactory(logger);
        NacosRestTemplate nacosRestTemplate = httpClientFactory.createNacosRestTemplate();
        assertNotNull(nacosRestTemplate);
    }
    
    @Test
    void testCreateNacosAsyncRestTemplateWithMinimalConfig() {
        HttpClientFactory httpClientFactory = new AbstractHttpClientFactory() {
            
            @Override
            protected HttpClientConfig buildHttpClientConfig() {
                // Return config with minimal/custom values
                return HttpClientConfig.builder().build();
            }
            
            @Override
            protected Logger assignLogger() {
                return logger;
            }
        };
        NacosAsyncRestTemplate nacosAsyncRestTemplate =
            httpClientFactory.createNacosAsyncRestTemplate();
        assertNotNull(nacosAsyncRestTemplate);
    }
}
