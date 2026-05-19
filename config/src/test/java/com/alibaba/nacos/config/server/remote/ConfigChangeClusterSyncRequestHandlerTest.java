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

package com.alibaba.nacos.config.server.remote;

import com.alibaba.nacos.api.config.remote.request.cluster.ConfigChangeClusterSyncRequest;
import com.alibaba.nacos.api.config.remote.response.cluster.ConfigChangeClusterSyncResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.config.server.configuration.ConfigCompatibleConfig;
import com.alibaba.nacos.config.server.service.ConfigMigrateService;
import com.alibaba.nacos.config.server.service.dump.DumpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ConfigChangeClusterSyncRequestHandlerTest {
    
    private ConfigChangeClusterSyncRequestHandler configChangeClusterSyncRequestHandler;
    
    @Mock
    private DumpService dumpService;
    
    @Mock
    private ConfigMigrateService configMigrateService;
    
    @BeforeEach
    void setUp() throws IOException {
        configChangeClusterSyncRequestHandler =
            new ConfigChangeClusterSyncRequestHandler(dumpService,
                configMigrateService);
    }
    
    @Test
    void testHandle() throws NacosException {
        ConfigChangeClusterSyncRequest configChangeSyncRequest =
            new ConfigChangeClusterSyncRequest();
        configChangeSyncRequest.setRequestId("");
        configChangeSyncRequest.setDataId("dataId");
        configChangeSyncRequest.setTag("tag");
        configChangeSyncRequest.setLastModified(1L);
        configChangeSyncRequest.setBeta(false);
        RequestMeta meta = new RequestMeta();
        meta.setClientIp("1.1.1.1");
        ConfigChangeClusterSyncResponse configChangeClusterSyncResponse =
            configChangeClusterSyncRequestHandler.handle(
                configChangeSyncRequest, meta);
        assertEquals(configChangeClusterSyncResponse.getResultCode(),
            ResponseCode.SUCCESS.getCode());
    }
    
    @Test
    void testHandleBetaCompatibleFromOldServer() throws NacosException {
        ConfigChangeClusterSyncRequest configChangeSyncRequest =
            new ConfigChangeClusterSyncRequest();
        configChangeSyncRequest.setRequestId("");
        configChangeSyncRequest.setDataId("dataId");
        configChangeSyncRequest.setGroup("group123");
        configChangeSyncRequest.setTenant("tenant...");
        configChangeSyncRequest.setLastModified(1L);
        configChangeSyncRequest.setBeta(true);
        RequestMeta meta = new RequestMeta();
        meta.setClientIp("1.1.1.1");
        ConfigChangeClusterSyncResponse configChangeClusterSyncResponse =
            configChangeClusterSyncRequestHandler.handle(
                configChangeSyncRequest, meta);
        verify(configMigrateService, times(1)).checkMigrateBeta(configChangeSyncRequest.getDataId(),
            configChangeSyncRequest.getGroup(), configChangeSyncRequest.getTenant());
        assertEquals(configChangeClusterSyncResponse.getResultCode(),
            ResponseCode.SUCCESS.getCode());
    }
    
    @Test
    void testHandleOldCompatibleFromOldServer() throws NacosException {
        ConfigChangeClusterSyncRequest configChangeSyncRequest =
            new ConfigChangeClusterSyncRequest();
        configChangeSyncRequest.setRequestId("");
        configChangeSyncRequest.setDataId("dataId");
        configChangeSyncRequest.setGroup("group123");
        configChangeSyncRequest.setTenant("tenant...");
        configChangeSyncRequest.setTag("tag1234");
        configChangeSyncRequest.setLastModified(1L);
        RequestMeta meta = new RequestMeta();
        meta.setClientIp("1.1.1.1");
        ConfigChangeClusterSyncResponse configChangeClusterSyncResponse =
            configChangeClusterSyncRequestHandler.handle(
                configChangeSyncRequest, meta);
        verify(configMigrateService, times(1)).checkMigrateTag(configChangeSyncRequest.getDataId(),
            configChangeSyncRequest.getGroup(), configChangeSyncRequest.getTenant(),
            configChangeSyncRequest.getTag());
        assertEquals(configChangeClusterSyncResponse.getResultCode(),
            ResponseCode.SUCCESS.getCode());
    }
    
    @Test
    void testCheckNamespaceCompatibleDisabled() {
        MockedStatic<ConfigCompatibleConfig> mocked =
            Mockito.mockStatic(ConfigCompatibleConfig.class);
        ConfigCompatibleConfig config = Mockito.mock(ConfigCompatibleConfig.class);
        Mockito.when(config.isNamespaceCompatibleMode()).thenReturn(false);
        mocked.when(ConfigCompatibleConfig::getInstance).thenReturn(config);
        try {
            ConfigChangeClusterSyncRequest req = new ConfigChangeClusterSyncRequest();
            req.setTenant("public");
            RequestMeta meta = new RequestMeta();
            meta.setClientVersion("Nacos-Server:v3.0.0");
            assertFalse(configChangeClusterSyncRequestHandler.checkNamespaceCompatible(req, meta));
        } finally {
            mocked.close();
        }
    }
    
    @Test
    void testCheckNamespaceCompatibleNewVersion() {
        MockedStatic<ConfigCompatibleConfig> mocked =
            Mockito.mockStatic(ConfigCompatibleConfig.class);
        ConfigCompatibleConfig config = Mockito.mock(ConfigCompatibleConfig.class);
        Mockito.when(config.isNamespaceCompatibleMode()).thenReturn(true);
        mocked.when(ConfigCompatibleConfig::getInstance).thenReturn(config);
        try {
            ConfigChangeClusterSyncRequest req = new ConfigChangeClusterSyncRequest();
            req.setTenant("public");
            RequestMeta meta = new RequestMeta();
            meta.setClientVersion("Nacos-Server:v3.0.0");
            assertFalse(configChangeClusterSyncRequestHandler
                .checkNamespaceCompatible(req, meta));
        } finally {
            mocked.close();
        }
    }
    
    @Test
    void testCheckNamespaceCompatibleOldVersion() {
        MockedStatic<ConfigCompatibleConfig> mocked =
            Mockito.mockStatic(ConfigCompatibleConfig.class);
        ConfigCompatibleConfig config = Mockito.mock(ConfigCompatibleConfig.class);
        Mockito.when(config.isNamespaceCompatibleMode()).thenReturn(true);
        mocked.when(ConfigCompatibleConfig::getInstance).thenReturn(config);
        try {
            ConfigChangeClusterSyncRequest req = new ConfigChangeClusterSyncRequest();
            req.setTenant("public");
            RequestMeta meta = new RequestMeta();
            meta.setClientVersion("Nacos-Java-Client:v2.1.0");
            assertTrue(configChangeClusterSyncRequestHandler
                .checkNamespaceCompatible(req, meta));
        } finally {
            mocked.close();
        }
    }
    
    @Test
    void testCheckNamespaceCompatibleInvalidVersion() {
        MockedStatic<ConfigCompatibleConfig> mocked =
            Mockito.mockStatic(ConfigCompatibleConfig.class);
        ConfigCompatibleConfig config = Mockito.mock(ConfigCompatibleConfig.class);
        Mockito.when(config.isNamespaceCompatibleMode()).thenReturn(true);
        mocked.when(ConfigCompatibleConfig::getInstance).thenReturn(config);
        try {
            ConfigChangeClusterSyncRequest req = new ConfigChangeClusterSyncRequest();
            req.setTenant("");
            RequestMeta meta = new RequestMeta();
            meta.setClientVersion("unknown-client");
            assertTrue(configChangeClusterSyncRequestHandler
                .checkNamespaceCompatible(req, meta));
        } finally {
            mocked.close();
        }
    }
}
