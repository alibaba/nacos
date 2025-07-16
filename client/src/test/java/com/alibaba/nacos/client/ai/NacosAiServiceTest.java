/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.ai;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.listener.AbstractNacosMcpServerListener;
import com.alibaba.nacos.api.ai.listener.NacosMcpServerEvent;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.client.ai.cache.NacosMcpServerCacheHolder;
import com.alibaba.nacos.client.ai.event.McpServerChangeNotifier;
import com.alibaba.nacos.client.ai.event.McpServerListenerInvoker;
import com.alibaba.nacos.client.ai.remote.AiGrpcClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NacosAiServiceTest {
    
    @Mock
    private AiGrpcClient grpcClient;
    
    @Mock
    private NacosMcpServerCacheHolder cacheHolder;
    
    @Mock
    private McpServerChangeNotifier mcpServerNotifier;
    
    NacosAiService nacosAiService;
    
    @BeforeEach
    void setUp() throws NacosException {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1");
        nacosAiService = new NacosAiService(properties);
    }
    
    @AfterEach
    void tearDown() throws NacosException {
        if (null != nacosAiService) {
            nacosAiService.shutdown();
        }
    }
    
    @Test
    void testConstructorWithNamespace() throws NoSuchFieldException, IllegalAccessException, NacosException {
        Field field = NacosAiService.class.getDeclaredField("namespaceId");
        field.setAccessible(true);
        assertEquals(Constants.DEFAULT_NAMESPACE_ID, field.get(nacosAiService));
        NacosAiService aiService = null;
        try {
            Properties properties = new Properties();
            properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1");
            properties.put(PropertyKeyConst.NAMESPACE, "test");
            aiService = new NacosAiService(properties);
            assertEquals("test", field.get(aiService));
        } finally {
            if (null != aiService) {
                aiService.shutdown();
            }
        }
    }
    
    @Test
    void getMcpServer() throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        when(grpcClient.queryMcpServer("testMcpName", "1.0.0")).thenReturn(new McpServerDetailInfo());
        assertNotNull(nacosAiService.getMcpServer("testMcpName", "1.0.0"));
    }
    
    @Test
    void getMcpServerWithInvalidMcpName() throws NoSuchFieldException, IllegalAccessException, NacosException {
        assertThrows(NacosApiException.class, () -> nacosAiService.getMcpServer("", "1.0.0"));
    }
    
    @Test
    void releaseMcpServer() throws NacosException, NoSuchFieldException, IllegalAccessException {
        injectMocks();
        McpServerBasicInfo serverSpecification = new McpServerBasicInfo();
        serverSpecification.setName("testMcpName");
        serverSpecification.setVersionDetail(new ServerVersionDetail());
        serverSpecification.getVersionDetail().setVersion("1.0.0");
        String id = UUID.randomUUID().toString();
        when(grpcClient.releaseMcpServer(serverSpecification, null)).thenReturn(id);
        assertEquals(id, nacosAiService.releaseMcpServer(serverSpecification, null));
    }
    
    @Test
    void releaseMcpServerWithInvalidParameters() throws NacosException {
        assertThrows(NacosApiException.class, () -> nacosAiService.releaseMcpServer(null, null));
        McpServerBasicInfo serverSpecification = new McpServerBasicInfo();
        assertThrows(NacosApiException.class, () -> nacosAiService.releaseMcpServer(serverSpecification, null));
        serverSpecification.setName("testMcpName");
        assertThrows(NacosApiException.class, () -> nacosAiService.releaseMcpServer(serverSpecification, null));
        serverSpecification.setVersionDetail(new ServerVersionDetail());
        assertThrows(NacosApiException.class, () -> nacosAiService.releaseMcpServer(serverSpecification, null));
    }
    
    @Test
    void registerMcpServerEndpoint() throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        nacosAiService.registerMcpServerEndpoint("testMcpName", "1.1.1.1", 8848, "1.0.0");
        verify(grpcClient).registerMcpServerEndpoint("testMcpName", "1.1.1.1", 8848, "1.0.0");
    }
    
    @Test
    void registerMcpServerEndpointWithInvalidParameters() {
        assertThrows(NacosApiException.class, () -> nacosAiService.registerMcpServerEndpoint("", null, -1, "1.0.0"));
        assertThrows(NacosApiException.class,
                () -> nacosAiService.registerMcpServerEndpoint("testMcpName", null, -1, "1.0.0"));
        assertThrows(NacosApiException.class,
                () -> nacosAiService.registerMcpServerEndpoint("testMcpName", "1.1.1.1", -1, "1.0.0"));
    }
    
    @Test
    void deregisterMcpServerEndpoint() throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        nacosAiService.deregisterMcpServerEndpoint("testMcpName", "1.1.1.1", 8848);
        verify(grpcClient).deregisterMcpServerEndpoint("testMcpName", "1.1.1.1", 8848);
    }
    
    @Test
    void deregisterMcpServerEndpointWithInvalidParameters() {
        assertThrows(NacosApiException.class, () -> nacosAiService.deregisterMcpServerEndpoint("", null, -1));
        assertThrows(NacosApiException.class,
                () -> nacosAiService.deregisterMcpServerEndpoint("testMcpName", null, -1));
        assertThrows(NacosApiException.class,
                () -> nacosAiService.deregisterMcpServerEndpoint("testMcpName", "1.1.1.1", -1));
    }
    
    @Test
    void subscribeMcpServer() throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        AbstractNacosMcpServerListener listener = Mockito.mock(AbstractNacosMcpServerListener.class);
        McpServerDetailInfo expected = new McpServerDetailInfo();
        when(grpcClient.subscribeMcpServer("testMcpName")).thenReturn(expected);
        McpServerDetailInfo actual = nacosAiService.subscribeMcpServer("testMcpName", listener);
        assertEquals(expected, actual);
        verify(mcpServerNotifier).registerListener(eq("testMcpName"), any(McpServerListenerInvoker.class));
        verify(listener).onEvent(any(NacosMcpServerEvent.class));
    }
    
    @Test
    void subscribeMcpServerWithInvalidParameters() {
        assertThrows(NacosApiException.class, () -> nacosAiService.subscribeMcpServer("", null));
        assertThrows(NacosApiException.class, () -> nacosAiService.subscribeMcpServer("testMcpName", null));
    }
    
    @Test
    void unsubscribeMcpServer() throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        AbstractNacosMcpServerListener listener = Mockito.mock(AbstractNacosMcpServerListener.class);
        nacosAiService.unsubscribeMcpServer("testMcpName", listener);
        verify(mcpServerNotifier).deregisterListener(eq("testMcpName"), any(McpServerListenerInvoker.class));
        verify(grpcClient).unsubscribeMcpServer("testMcpName");
    }
    
    @Test
    void unsubscribeMcpServerWithOtherListener() throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        when(mcpServerNotifier.isSubscribed("testMcpName")).thenReturn(true);
        AbstractNacosMcpServerListener listener = Mockito.mock(AbstractNacosMcpServerListener.class);
        nacosAiService.unsubscribeMcpServer("testMcpName", listener);
        verify(mcpServerNotifier).deregisterListener(eq("testMcpName"), any(McpServerListenerInvoker.class));
        verify(grpcClient, never()).unsubscribeMcpServer("testMcpName");
    }
    
    @Test
    void unsubscribeMcpServerWithNullListener() throws NoSuchFieldException, IllegalAccessException, NacosException {
        injectMocks();
        nacosAiService.unsubscribeMcpServer("testMcpName", null);
        verify(mcpServerNotifier, never()).deregisterListener(eq("testMcpName"), any(McpServerListenerInvoker.class));
        verify(grpcClient, never()).unsubscribeMcpServer("testMcpName");
    }
    
    @Test
    void unsubscribeMcpServerWithInvalidParameters() {
        assertThrows(NacosApiException.class, () -> nacosAiService.unsubscribeMcpServer("", null));
    }
    
    private void injectMocks() throws NoSuchFieldException, IllegalAccessException {
        Field field = NacosAiService.class.getDeclaredField("grpcClient");
        field.setAccessible(true);
        AiGrpcClient autoBuildGrpcClient = (AiGrpcClient) field.get(nacosAiService);
        field.set(nacosAiService, grpcClient);
        field = NacosAiService.class.getDeclaredField("cacheHolder");
        field.setAccessible(true);
        NacosMcpServerCacheHolder autoBuildCacheHolder = (NacosMcpServerCacheHolder) field.get(nacosAiService);
        field.set(nacosAiService, cacheHolder);
        field = NacosAiService.class.getDeclaredField("mcpServerNotifier");
        field.setAccessible(true);
        field.set(nacosAiService, mcpServerNotifier);
        try {
            autoBuildGrpcClient.shutdown();
            autoBuildCacheHolder.shutdown();
        } catch (NacosException ignored) {
        }
    }
}