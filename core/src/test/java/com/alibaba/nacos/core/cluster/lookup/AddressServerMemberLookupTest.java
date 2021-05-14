/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.cluster.lookup;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.sys.env.EnvUtil;
import junit.framework.TestCase;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Type;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AddressServerMemberLookupTest extends TestCase {
    
    @Mock
    private NacosRestTemplate restTemplate;
    
    @Mock
    private ServerMemberManager memberManager;
    
    @Mock
    private HttpRestResult<String> result;
    
    private AddressServerMemberLookup addressServerMemberLookup;
    
    private String addressUrl;
    
    private String envIdUrl;
    
    private String addressServerUrl;
    
    private String addressPort;
    
    private String domainName;
    
    @Mock
    private ConfigurableEnvironment environment;
    
    @Before
    public void setUp() throws Exception {
        EnvUtil.setEnvironment(environment);
        when(environment.getProperty("maxHealthCheckFailCount", "12")).thenReturn("12");
        when(environment.getProperty("nacos.core.address-server.retry", Integer.class, 5)).thenReturn(5);
        when(environment.getProperty("address.server.domain", "jmenv.tbsite.net")).thenReturn("jmenv.tbsite.net");
        when(environment.getProperty("address.server.port", "8080")).thenReturn("8080");
        when(environment.getProperty("address.server.domain", "jmenv.tbsite.net")).thenReturn("jmenv.tbsite.net");
        when(environment.getProperty(eq("address.server.url"), any(String.class))).thenReturn("/nacos/serverlist");
        initAddressSys();
        when(restTemplate.<String>get(eq(addressServerUrl), any(Header.EMPTY.getClass()), any(Query.EMPTY.getClass()), any(Type.class)))
                .thenReturn(result);
        addressServerMemberLookup = new AddressServerMemberLookup();
        ReflectionTestUtils.setField(addressServerMemberLookup, "restTemplate", restTemplate);
        
        when(result.ok()).thenReturn(true);
        when(result.getData()).thenReturn("1.1.1.1:8848");
        addressServerMemberLookup.start();
    }
    
    @After
    public void tearDown() throws NacosException {
        addressServerMemberLookup.destroy();
    }
    
    @Test
    public void testMemberChange() throws Exception {
        addressServerMemberLookup.injectMemberManager(memberManager);
        verify(restTemplate).get(eq(addressServerUrl), any(Header.EMPTY.getClass()), any(Query.EMPTY.getClass()), any(Type.class));
    }
    
    @Test
    public void testInfo() {
        Map<String, Object> infos =  addressServerMemberLookup.info();
        assertEquals(4, infos.size());
        assertTrue(infos.containsKey("addressServerHealth"));
        assertTrue(infos.containsKey("addressServerUrl"));
        assertTrue(infos.containsKey("envIdUrl"));
        assertTrue(infos.containsKey("addressServerFailCount"));
        assertEquals(addressServerUrl, infos.get("addressServerUrl"));
        assertEquals(envIdUrl, infos.get("envIdUrl"));
    }
    
    private void initAddressSys() {
        String envDomainName = System.getenv("address_server_domain");
        if (StringUtils.isBlank(envDomainName)) {
            domainName = EnvUtil.getProperty("address.server.domain", "jmenv.tbsite.net");
        } else {
            domainName = envDomainName;
        }
        String envAddressPort = System.getenv("address_server_port");
        if (StringUtils.isBlank(envAddressPort)) {
            addressPort = EnvUtil.getProperty("address.server.port", "8080");
        } else {
            addressPort = envAddressPort;
        }
        String envAddressUrl = System.getenv("address_server_url");
        if (StringUtils.isBlank(envAddressUrl)) {
            addressUrl = EnvUtil.getProperty("address.server.url", EnvUtil.getContextPath() + "/" + "serverlist");
        } else {
            addressUrl = envAddressUrl;
        }
        addressServerUrl = "http://" + domainName + ":" + addressPort + addressUrl;
        envIdUrl = "http://" + domainName + ":" + addressPort + "/env";
        
        Loggers.CORE.info("ServerListService address-server port:" + addressPort);
        Loggers.CORE.info("ADDRESS_SERVER_URL:" + addressServerUrl);
    }
}