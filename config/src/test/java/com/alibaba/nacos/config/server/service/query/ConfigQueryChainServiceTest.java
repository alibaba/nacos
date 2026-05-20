/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service.query;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.config.server.exception.NacosConfigException;
import com.alibaba.nacos.config.server.service.query.enums.ResponseCode;
import com.alibaba.nacos.config.server.service.query.handler.ConfigQueryHandler;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigQueryChainServiceTest {
    
    @Test
    void testConstructorThrowsWhenBuilderMissing() {
        try (MockedStatic<EnvUtil> envUtilMock = Mockito.mockStatic(EnvUtil.class);
            MockedStatic<NacosServiceLoader> serviceLoaderMock =
                Mockito.mockStatic(NacosServiceLoader.class)) {
            ConfigQueryHandlerChainBuilder builder = mock(ConfigQueryHandlerChainBuilder.class);
            when(builder.getName()).thenReturn("other");
            envUtilMock.when(() -> EnvUtil.getProperty("nacos.config.query.chain.builder", "nacos"))
                .thenReturn("missing");
            serviceLoaderMock.when(
                () -> NacosServiceLoader.load(ConfigQueryHandlerChainBuilder.class))
                .thenReturn(Collections.singletonList(builder));
            
            assertThrows(NacosConfigException.class, ConfigQueryChainService::new);
        }
    }
    
    @Test
    void testHandleReturnsChainResponse() throws Exception {
        ConfigQueryChainResponse expectedResponse = new ConfigQueryChainResponse();
        ConfigQueryChainService service = createServiceWithHandler(expectedResponse, null);
        
        ConfigQueryChainResponse actualResponse = service.handle(new ConfigQueryChainRequest());
        
        assertSame(expectedResponse, actualResponse);
    }
    
    @Test
    void testHandleReturnsFailResponseWhenChainThrowsException() throws Exception {
        ConfigQueryChainService service = createServiceWithHandler(null, new IOException("disk"));
        
        ConfigQueryChainResponse actualResponse = service.handle(new ConfigQueryChainRequest());
        
        assertEquals(ResponseCode.FAIL.getCode(), actualResponse.getResultCode());
        assertEquals("disk", actualResponse.getMessage());
    }
    
    @Test
    void testAddNullHandlerReturnsSameChain() {
        ConfigQueryHandlerChain chain = new ConfigQueryHandlerChain();
        
        assertSame(chain, chain.addHandler(null));
    }
    
    private ConfigQueryChainService createServiceWithHandler(ConfigQueryChainResponse response,
        IOException exception) throws Exception {
        try (MockedStatic<EnvUtil> envUtilMock = Mockito.mockStatic(EnvUtil.class);
            MockedStatic<NacosServiceLoader> serviceLoaderMock =
                Mockito.mockStatic(NacosServiceLoader.class)) {
            ConfigQueryHandler handler = mock(ConfigQueryHandler.class);
            if (exception == null) {
                when(handler.handle(any(ConfigQueryChainRequest.class))).thenReturn(response);
            } else {
                when(handler.handle(any(ConfigQueryChainRequest.class))).thenThrow(exception);
            }
            ConfigQueryHandlerChain chain = new ConfigQueryHandlerChain().addHandler(handler);
            ConfigQueryHandlerChainBuilder builder = mock(ConfigQueryHandlerChainBuilder.class);
            when(builder.getName()).thenReturn("nacos");
            when(builder.build()).thenReturn(chain);
            envUtilMock.when(() -> EnvUtil.getProperty("nacos.config.query.chain.builder", "nacos"))
                .thenReturn("nacos");
            serviceLoaderMock.when(
                () -> NacosServiceLoader.load(ConfigQueryHandlerChainBuilder.class))
                .thenReturn(Collections.singletonList(builder));
            return new ConfigQueryChainService();
        }
    }
}
