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

package impl;

import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.plugin.address.common.AddressProperties;
import com.alibaba.nacos.plugin.address.impl.EndpointAddressPlugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Map;

import static com.alibaba.nacos.common.constant.RequestUrlConstants.HTTP_PREFIX;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Date 2022/7/31.
 *
 * @author GuoJiangFu
 */
@RunWith(MockitoJUnitRunner.class)
public class EndpointAddressPluginTest {
    
    @Mock
    private NacosRestTemplate restTemplate;
    
    @Mock
    private HttpRestResult<String> result;
    
    private EndpointAddressPlugin endpointAddressPlugin;
    
    private String addressUrl;
    
    private String envIdUrl;
    
    private String addressServerUrl;
    
    private String addressPort;
    
    private String domainName;
    
    @Before
    public void setUp() throws Exception {
        initAddressSys();
        when(restTemplate.<String>get(eq(addressServerUrl), any(Header.EMPTY.getClass()), any(Query.EMPTY.getClass()), any(
                Type.class)))
                .thenReturn(result);
        endpointAddressPlugin = new EndpointAddressPlugin();
        Class<EndpointAddressPlugin> endpointAddressPluginClass = EndpointAddressPlugin.class;
        Field restTemplate = endpointAddressPluginClass.getDeclaredField("restTemplate");
        restTemplate.setAccessible(true);
        restTemplate.set(endpointAddressPlugin, this.restTemplate);
        when(result.ok()).thenReturn(true);
        when(result.getData()).thenReturn("1.1.1.1:8848");
        endpointAddressPlugin.start();
    }
    
    @After
    public void tearShutdown(){
        endpointAddressPlugin.shutdown();
    }
    
    @Test
    public void testInfo() {
        Map<String, Object> infos =  endpointAddressPlugin.info();
        assertEquals(4, infos.size());
        assertTrue(infos.containsKey("addressServerHealth"));
        assertTrue(infos.containsKey("addressServerUrl"));
        assertTrue(infos.containsKey("envIdUrl"));
        assertTrue(infos.containsKey("addressServerFailCount"));
        assertEquals(addressServerUrl, infos.get("addressServerUrl"));
        assertEquals(envIdUrl, infos.get("envIdUrl"));
    }
    
    @Test
    public void testSyncFromAddressUrl() throws Exception {
        RestResult<String> result = restTemplate
                .get(addressServerUrl, Header.EMPTY, Query.EMPTY, String.class);
        assertEquals("1.1.1.1:8848", result.getData());
    }
    
    @Test
    public void testGetPluginName() {
        assertEquals("address-server", endpointAddressPlugin.getPluginName());
    }
    
    private void initAddressSys() {
        this.domainName = "jmenv.tbsite.net";
        this.addressPort = "8080";
        this.addressUrl = "/nacos/serverlist";
        addressServerUrl = HTTP_PREFIX + domainName + ":" + addressPort + addressUrl;
        envIdUrl = HTTP_PREFIX + domainName + ":" + addressPort + "/env";
        AddressProperties.setProperties("addressServerUrl", addressServerUrl);
        AddressProperties.setProperties("envIdUrl", envIdUrl);
    }
}
