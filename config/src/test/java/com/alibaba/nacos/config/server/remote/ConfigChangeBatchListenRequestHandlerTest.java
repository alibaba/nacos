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

import com.alibaba.nacos.api.config.remote.request.ConfigBatchListenRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigChangeBatchListenResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.core.utils.StringPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class ConfigChangeBatchListenRequestHandlerTest {
    
    @InjectMocks
    private ConfigChangeBatchListenRequestHandler configQueryRequestHandler;
    
    @InjectMocks
    private ConfigChangeListenContext configChangeListenContext;
    
    private RequestMeta requestMeta;
    
    @BeforeEach
    void setUp() {
        configQueryRequestHandler = new ConfigChangeBatchListenRequestHandler();
        ReflectionTestUtils.setField(configQueryRequestHandler, "configChangeListenContext", configChangeListenContext);
        requestMeta = new RequestMeta();
        requestMeta.setClientIp("1.1.1.1");
    }
    
    @Test
    void testHandle() {
        MockedStatic<ConfigCacheService> configCacheServiceMockedStatic = Mockito.mockStatic(ConfigCacheService.class);
        
        String dataId = "dataId";
        String group = "group";
        String tenant = "tenant";
        String groupKey = GroupKey2.getKey(dataId, group, tenant);
        groupKey = StringPool.get(groupKey);
        
        final String groupKeyCopy = groupKey;
        configCacheServiceMockedStatic.when(
                () -> ConfigCacheService.isUptodate(eq(groupKeyCopy), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(false);
        ConfigBatchListenRequest configChangeListenRequest = new ConfigBatchListenRequest();
        configChangeListenRequest.addConfigListenContext(group, dataId, tenant, " ");
        try {
            ConfigChangeBatchListenResponse configChangeBatchListenResponse = configQueryRequestHandler.handle(configChangeListenRequest,
                    requestMeta);
            boolean hasChange = false;
            for (ConfigChangeBatchListenResponse.ConfigContext changedConfig : configChangeBatchListenResponse.getChangedConfigs()) {
                if (changedConfig.getDataId().equals(dataId)) {
                    hasChange = true;
                    break;
                }
            }
            assertTrue(hasChange);
        } catch (NacosException e) {
            e.printStackTrace();
        } finally {
            configCacheServiceMockedStatic.close();
        }
    }
    
}