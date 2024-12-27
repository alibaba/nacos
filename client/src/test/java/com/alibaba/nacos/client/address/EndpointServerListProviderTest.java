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

package com.alibaba.nacos.client.address;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.SystemPropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.constant.Constants;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.utils.ContextPathUtil;
import com.alibaba.nacos.client.utils.ParamUtil;
import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EndpointServerListProviderTest {
    
    @Mock
    NacosRestTemplate nacosRestTemplate;
    
    private EndpointServerListProvider serverListProvider;
    
    private NacosClientProperties properties;
    
    private HttpRestResult requestSuccess;
    
    @BeforeEach
    void setUp() {
        requestSuccess = new HttpRestResult<>(Header.EMPTY, 200, "\n127.0.0.1\nlocalhost:9848", "success");
        serverListProvider = new EndpointServerListProvider();
        properties = NacosClientProperties.PROTOTYPE.derive();
    }
    
    @AfterEach
    void tearDown() throws NacosException {
        System.clearProperty(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_ENDPOINT_URL);
        System.clearProperty(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_ENDPOINT_PORT);
        System.clearProperty(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_ENDPOINT_CONTEXT_PATH);
        System.clearProperty(PropertyKeyConst.ENDPOINT_CLUSTER_NAME);
        System.clearProperty(SystemPropertyKeyConst.IS_USE_ENDPOINT_PARSING_RULE);
        serverListProvider.shutdown();
    }
    
    @Test
    void testInitWithoutProperties() throws NacosException {
        assertThrows(NacosException.class, () -> serverListProvider.init(null, nacosRestTemplate));
    }
    
    @Test
    void testMatchAndInitForPropertiesEndpoint() throws Exception {
        assertFalse(serverListProvider.match(properties));
        properties.setProperty(PropertyKeyConst.ENDPOINT, "endpointFromProperties");
        assertTrue(serverListProvider.match(properties));
        when(nacosRestTemplate.get(anyString(), any(Header.class), any(Query.class), eq(String.class))).thenReturn(
                requestSuccess);
        serverListProvider.init(properties, nacosRestTemplate);
        assertInit("endpointFromProperties", 8080, ParamUtil.getDefaultContextPath(), ParamUtil.getDefaultNodesPath(),
                "", ParamUtil.getDefaultContextPath());
        assertEquals(2, serverListProvider.getServerList().size());
    }
    
    @Test
    void testMatchAndInitForSystemEndpoint() throws Exception {
        assertFalse(serverListProvider.match(properties));
        System.setProperty(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_ENDPOINT_URL, "endpointFromSystem");
        when(nacosRestTemplate.get(anyString(), any(Header.class), any(Query.class), eq(String.class))).thenReturn(
                requestSuccess);
        assertTrue(serverListProvider.match(properties));
        serverListProvider.init(properties, nacosRestTemplate);
        assertInit("endpointFromSystem", 8080, ParamUtil.getDefaultContextPath(), ParamUtil.getDefaultNodesPath(), "",
                ParamUtil.getDefaultContextPath());
        assertEquals(2, serverListProvider.getServerList().size());
    }
    
    @Test
    void testMatchAndInitByParsingFalseFromProperties() throws Exception {
        assertFalse(serverListProvider.match(properties));
        properties.setProperty(PropertyKeyConst.ENDPOINT, "endpointFromProperties");
        System.setProperty(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_ENDPOINT_URL, "endpointFromSystem");
        properties.setProperty(PropertyKeyConst.IS_USE_ENDPOINT_PARSING_RULE, "false");
        assertTrue(serverListProvider.match(properties));
        when(nacosRestTemplate.get(anyString(), any(Header.class), any(Query.class), eq(String.class))).thenReturn(
                requestSuccess);
        serverListProvider.init(properties, nacosRestTemplate);
        assertInit("endpointFromProperties", 8080, ParamUtil.getDefaultContextPath(), ParamUtil.getDefaultNodesPath(),
                "", ParamUtil.getDefaultContextPath());
        assertEquals(2, serverListProvider.getServerList().size());
    }
    
    @Test
    void testMatchAndInitByParsingFalseFromSystem() throws Exception {
        assertFalse(serverListProvider.match(properties));
        properties.setProperty(PropertyKeyConst.ENDPOINT, "endpointFromProperties");
        System.setProperty(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_ENDPOINT_URL, "endpointFromSystem");
        System.setProperty(SystemPropertyKeyConst.IS_USE_ENDPOINT_PARSING_RULE, "false");
        assertTrue(serverListProvider.match(properties));
        when(nacosRestTemplate.get(anyString(), any(Header.class), any(Query.class), eq(String.class))).thenReturn(
                requestSuccess);
        serverListProvider.init(properties, nacosRestTemplate);
        assertInit("endpointFromProperties", 8080, ParamUtil.getDefaultContextPath(), ParamUtil.getDefaultNodesPath(),
                "", ParamUtil.getDefaultContextPath());
        assertEquals(2, serverListProvider.getServerList().size());
    }
    
    @Test
    void testMatchAndInitByParsingTrue() throws Exception {
        assertFalse(serverListProvider.match(properties));
        properties.setProperty(PropertyKeyConst.ENDPOINT, "endpointFromProperties");
        System.setProperty(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_ENDPOINT_URL, "endpointFromSystem");
        assertTrue(serverListProvider.match(properties));
        when(nacosRestTemplate.get(anyString(), any(Header.class), any(Query.class), eq(String.class))).thenReturn(
                requestSuccess);
        serverListProvider.init(properties, nacosRestTemplate);
        assertInit("endpointFromSystem", 8080, ParamUtil.getDefaultContextPath(), ParamUtil.getDefaultNodesPath(), "",
                ParamUtil.getDefaultContextPath());
        assertEquals(2, serverListProvider.getServerList().size());
    }
    
    @Test
    void testInitWithPropertiesEndpointPort() throws Exception {
        properties.setProperty(PropertyKeyConst.ENDPOINT, "endpointFromProperties");
        properties.setProperty(PropertyKeyConst.ENDPOINT_PORT, "80");
        when(nacosRestTemplate.get(anyString(), any(Header.class), any(Query.class), eq(String.class))).thenReturn(
                requestSuccess);
        serverListProvider.init(properties, nacosRestTemplate);
        assertInit("endpointFromProperties", 80, ParamUtil.getDefaultContextPath(), ParamUtil.getDefaultNodesPath(), "",
                ParamUtil.getDefaultContextPath());
        assertEquals(2, serverListProvider.getServerList().size());
    }
    
    @Test
    void testInitWithSystemEndpointPort() throws Exception {
        properties.setProperty(PropertyKeyConst.ENDPOINT, "endpointFromProperties");
        System.setProperty(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_ENDPOINT_PORT, "443");
        when(nacosRestTemplate.get(anyString(), any(Header.class), any(Query.class), eq(String.class))).thenReturn(
                requestSuccess);
        serverListProvider.init(properties, nacosRestTemplate);
        assertInit("endpointFromProperties", 443, ParamUtil.getDefaultContextPath(), ParamUtil.getDefaultNodesPath(),
                "", ParamUtil.getDefaultContextPath());
        assertEquals(2, serverListProvider.getServerList().size());
    }
    
    @Test
    void testInitWithPropertiesEndpointContextPath() throws Exception {
        properties.setProperty(PropertyKeyConst.ENDPOINT, "endpointFromProperties");
        properties.setProperty(PropertyKeyConst.ENDPOINT_CONTEXT_PATH, "address");
        when(nacosRestTemplate.get(anyString(), any(Header.class), any(Query.class), eq(String.class))).thenReturn(
                requestSuccess);
        serverListProvider.init(properties, nacosRestTemplate);
        assertInit("endpointFromProperties", 8080, "address", ParamUtil.getDefaultNodesPath(), "",
                ParamUtil.getDefaultContextPath());
        assertEquals(2, serverListProvider.getServerList().size());
    }
    
    @Test
    void testInitWithSystemEndpointContextPath() throws Exception {
        properties.setProperty(PropertyKeyConst.ENDPOINT, "endpointFromProperties");
        System.setProperty(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_ENDPOINT_CONTEXT_PATH, "addresses");
        when(nacosRestTemplate.get(anyString(), any(Header.class), any(Query.class), eq(String.class))).thenReturn(
                requestSuccess);
        serverListProvider.init(properties, nacosRestTemplate);
        assertInit("endpointFromProperties", 8080, "addresses", ParamUtil.getDefaultNodesPath(), "",
                ParamUtil.getDefaultContextPath());
        assertEquals(2, serverListProvider.getServerList().size());
    }
    
    @Test
    void testInitContextPathWithFull() throws Exception {
        properties.setProperty(PropertyKeyConst.ENDPOINT, "endpointFromProperties");
        properties.setProperty(PropertyKeyConst.CONTEXT_PATH, "globalContextPath");
        when(nacosRestTemplate.get(anyString(), any(Header.class), any(Query.class), eq(String.class))).thenReturn(
                requestSuccess);
        serverListProvider.init(properties, nacosRestTemplate);
        assertInit("endpointFromProperties", 8080, "globalContextPath", ParamUtil.getDefaultNodesPath(), "",
                "globalContextPath");
        assertEquals(2, serverListProvider.getServerList().size());
    }
    
    @Test
    void testInitWithPropertiesEndpointClusterName() throws Exception {
        properties.setProperty(PropertyKeyConst.ENDPOINT, "endpointFromProperties");
        properties.setProperty(PropertyKeyConst.ENDPOINT_CLUSTER_NAME, "endpointClusterName");
        when(nacosRestTemplate.get(anyString(), any(Header.class), any(Query.class), eq(String.class))).thenReturn(
                requestSuccess);
        serverListProvider.init(properties, nacosRestTemplate);
        assertInit("endpointFromProperties", 8080, ParamUtil.getDefaultContextPath(), "endpointClusterName", "",
                ParamUtil.getDefaultContextPath());
        assertEquals(2, serverListProvider.getServerList().size());
    }
    
    @Test
    void testInitWithPropertiesEndpointClusterNameWithFull() throws Exception {
        properties.setProperty(PropertyKeyConst.ENDPOINT, "endpointFromProperties");
        properties.setProperty(PropertyKeyConst.CLUSTER_NAME, "clusterName");
        properties.setProperty(PropertyKeyConst.ENDPOINT_CLUSTER_NAME, "endpointClusterName");
        properties.setProperty(PropertyKeyConst.IS_ADAPT_CLUSTER_NAME_USAGE, "true");
        when(nacosRestTemplate.get(anyString(), any(Header.class), any(Query.class), eq(String.class))).thenReturn(
                requestSuccess);
        serverListProvider.init(properties, nacosRestTemplate);
        assertInit("endpointFromProperties", 8080, ParamUtil.getDefaultContextPath(), "endpointClusterName", "",
                ParamUtil.getDefaultContextPath());
        assertEquals(2, serverListProvider.getServerList().size());
    }
    
    @Test
    void testInitWithSystemEndpointClusterNameByOldWay() throws Exception {
        properties.setProperty(PropertyKeyConst.ENDPOINT, "endpointFromProperties");
        properties.setProperty(PropertyKeyConst.CLUSTER_NAME, "clusterName");
        properties.setProperty(PropertyKeyConst.IS_ADAPT_CLUSTER_NAME_USAGE, "true");
        when(nacosRestTemplate.get(anyString(), any(Header.class), any(Query.class), eq(String.class))).thenReturn(
                requestSuccess);
        serverListProvider.init(properties, nacosRestTemplate);
        assertInit("endpointFromProperties", 8080, ParamUtil.getDefaultContextPath(), "clusterName", "",
                ParamUtil.getDefaultContextPath());
        assertEquals(2, serverListProvider.getServerList().size());
    }
    
    @Test
    void testInitWithSystemEndpointClusterWithoutAdapt() throws Exception {
        properties.setProperty(PropertyKeyConst.ENDPOINT, "endpointFromProperties");
        properties.setProperty(PropertyKeyConst.CLUSTER_NAME, "clusterName");
        when(nacosRestTemplate.get(anyString(), any(Header.class), any(Query.class), eq(String.class))).thenReturn(
                requestSuccess);
        serverListProvider.init(properties, nacosRestTemplate);
        assertInit("endpointFromProperties", 8080, ParamUtil.getDefaultContextPath(), ParamUtil.getDefaultNodesPath(),
                "", ParamUtil.getDefaultContextPath());
        assertEquals(2, serverListProvider.getServerList().size());
    }
    
    @Test
    void testInitWithNamespace() throws Exception {
        properties.setProperty(PropertyKeyConst.ENDPOINT, "endpointFromProperties");
        properties.setProperty(PropertyKeyConst.NAMESPACE, "customNamespace");
        when(nacosRestTemplate.get(anyString(), any(Header.class), any(Query.class), eq(String.class))).thenReturn(
                requestSuccess);
        serverListProvider.init(properties, nacosRestTemplate);
        assertInit("endpointFromProperties", 8080, ParamUtil.getDefaultContextPath(), ParamUtil.getDefaultNodesPath(),
                "customNamespace", ParamUtil.getDefaultContextPath());
        assertEquals(2, serverListProvider.getServerList().size());
    }
    
    @Test
    void testInitWithQuery() throws Exception {
        properties.setProperty(PropertyKeyConst.ENDPOINT, "endpointFromProperties");
        properties.setProperty(PropertyKeyConst.ENDPOINT_QUERY_PARAMS, "nofix=1");
        when(nacosRestTemplate.get(anyString(), any(Header.class), any(Query.class), eq(String.class))).thenReturn(
                requestSuccess);
        serverListProvider.init(properties, nacosRestTemplate);
        assertInit("endpointFromProperties", 8080, ParamUtil.getDefaultContextPath(), ParamUtil.getDefaultNodesPath(),
                "", ParamUtil.getDefaultContextPath(), "nofix=1");
        assertEquals(2, serverListProvider.getServerList().size());
    }
    
    @Test
    void testInitWithNamespaceAndQuery() throws Exception {
        properties.setProperty(PropertyKeyConst.ENDPOINT, "endpointFromProperties");
        properties.setProperty(PropertyKeyConst.NAMESPACE, "customNamespace");
        properties.setProperty(PropertyKeyConst.ENDPOINT_QUERY_PARAMS, "nofix=1");
        when(nacosRestTemplate.get(anyString(), any(Header.class), any(Query.class), eq(String.class))).thenReturn(
                requestSuccess);
        serverListProvider.init(properties, nacosRestTemplate);
        assertInit("endpointFromProperties", 8080, ParamUtil.getDefaultContextPath(), ParamUtil.getDefaultNodesPath(),
                "customNamespace", ParamUtil.getDefaultContextPath(), "nofix=1");
        assertEquals(2, serverListProvider.getServerList().size());
    }
    
    @Test
    void testInitWithModuleType() throws Exception {
        properties.setProperty(PropertyKeyConst.ENDPOINT, "endpointFromProperties");
        properties.setProperty(com.alibaba.nacos.api.common.Constants.CLIENT_MODULE_TYPE, "naming");
        when(nacosRestTemplate.get(anyString(),
                argThat(header -> "naming".equals(header.getValue(HttpHeaderConsts.REQUEST_MODULE))), any(Query.class),
                eq(String.class))).thenReturn(requestSuccess);
        serverListProvider.init(properties, nacosRestTemplate);
        assertFalse(serverListProvider.getServerList().isEmpty());
        assertEquals(2, serverListProvider.getServerList().size());
    }
    
    @Test
    void testInitGetServerListWithException() throws Exception {
        properties.setProperty(PropertyKeyConst.ENDPOINT, "endpointFromProperties");
        when(nacosRestTemplate.get(anyString(), any(Header.class), any(Query.class), eq(String.class))).thenThrow(
                new IOException("test"));
        assertThrows(NacosException.class, () -> serverListProvider.init(properties, nacosRestTemplate));
    }
    
    @Test
    void testInitGetServerListWithError() throws Exception {
        properties.setProperty(PropertyKeyConst.ENDPOINT, "endpointFromProperties");
        HttpRestResult failedResult = new HttpRestResult<>(Header.EMPTY, 500, null, "test");
        when(nacosRestTemplate.get(anyString(), any(Header.class), any(Query.class), eq(String.class))).thenReturn(
                failedResult);
        assertThrows(NacosException.class, () -> serverListProvider.init(properties, nacosRestTemplate));
    }
    
    @Test
    void testRefreshServerList() throws Exception {
        properties.setProperty(PropertyKeyConst.ENDPOINT, "endpointFromProperties");
        properties.setProperty(PropertyKeyConst.ENDPOINT_REFRESH_INTERVAL_SECONDS, "1");
        HttpRestResult newResult = new HttpRestResult<>(Header.EMPTY, 200, "\n1.1.1.1 \nlocalhost:9848", "success");
        when(nacosRestTemplate.get(anyString(), any(Header.class), any(Query.class), eq(String.class))).thenReturn(
                requestSuccess, newResult);
        serverListProvider.init(properties, nacosRestTemplate);
        assertEquals(2, serverListProvider.getServerList().size());
        assertEquals("127.0.0.1:8848", serverListProvider.getServerList().get(0));
        assertEquals("localhost:9848", serverListProvider.getServerList().get(1));
        Field field = EndpointServerListProvider.class.getDeclaredField("lastServerListRefreshTime");
        field.setAccessible(true);
        field.set(serverListProvider, 0L);
        // wait refresh
        TimeUnit.MILLISECONDS.sleep(2000);
        assertEquals(2, serverListProvider.getServerList().size());
        assertEquals("1.1.1.1:8848", serverListProvider.getServerList().get(0));
        assertEquals("localhost:9848", serverListProvider.getServerList().get(1));
    }
    
    @Test
    void testRefreshServerListWithDiffSort() throws Exception {
        properties.setProperty(PropertyKeyConst.ENDPOINT, "endpointFromProperties");
        properties.setProperty(PropertyKeyConst.ENDPOINT_REFRESH_INTERVAL_SECONDS, "1");
        HttpRestResult newResult = new HttpRestResult<>(Header.EMPTY, 200, "\nlocalhost:9848\n127.0.0.1", "success");
        when(nacosRestTemplate.get(anyString(), any(Header.class), any(Query.class), eq(String.class))).thenReturn(
                requestSuccess, newResult);
        serverListProvider.init(properties, nacosRestTemplate);
        assertEquals(2, serverListProvider.getServerList().size());
        assertEquals("127.0.0.1:8848", serverListProvider.getServerList().get(0));
        assertEquals("localhost:9848", serverListProvider.getServerList().get(1));
        Field field = EndpointServerListProvider.class.getDeclaredField("lastServerListRefreshTime");
        field.setAccessible(true);
        field.set(serverListProvider, 0L);
        // wait refresh
        TimeUnit.MILLISECONDS.sleep(2000);
        assertEquals(2, serverListProvider.getServerList().size());
        assertEquals("127.0.0.1:8848", serverListProvider.getServerList().get(0));
        assertEquals("localhost:9848", serverListProvider.getServerList().get(1));
    }
    
    private void assertInit(String expectedEndpoint, int expectEndpointPort, String expectedEndpointContext,
            String expectedServiceName, String expectedNamespace, String expectedContextPath) {
        assertInit(expectedEndpoint, expectEndpointPort, expectedEndpointContext, expectedServiceName,
                expectedNamespace, expectedContextPath, null);
    }
    
    private void assertInit(String expectedEndpoint, int expectEndpointPort, String expectedEndpointContext,
            String expectedServiceName, String expectedNamespace, String expectedContextPath, String expectedQuery) {
        String expectedAddressServerUrl = String.format("http://%s:%d%s/%s", expectedEndpoint, expectEndpointPort,
                ContextPathUtil.normalizeContextPath(expectedEndpointContext), expectedServiceName);
        assertEquals(Constants.Address.ENDPOINT_SERVER_LIST_PROVIDER_ORDER, serverListProvider.getOrder());
        String expectedServerName = String.format("%s-%s_%d_%s_%s", "custom", expectedEndpoint, expectEndpointPort,
                expectedEndpointContext, expectedServiceName);
        if (StringUtils.isNotBlank(expectedNamespace)) {
            expectedServerName = String.format("%s_%s", expectedServerName, expectedNamespace);
            expectedAddressServerUrl += "?namespace=" + expectedNamespace;
        }
        if (StringUtils.isNotBlank(expectedQuery)) {
            String queryTag = StringUtils.isBlank(expectedNamespace) ? "?" : "&";
            expectedAddressServerUrl += queryTag + expectedQuery;
        }
        assertEquals(expectedAddressServerUrl, serverListProvider.getAddressSource());
        assertEquals(expectedServerName, serverListProvider.getServerName());
        assertEquals(expectedNamespace, serverListProvider.getNamespace());
        assertEquals(expectedContextPath, serverListProvider.getContextPath());
    }
}