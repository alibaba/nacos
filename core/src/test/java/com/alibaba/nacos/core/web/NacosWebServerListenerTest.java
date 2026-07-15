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

package com.alibaba.nacos.core.web;

import com.alibaba.nacos.core.cluster.ServerMemberManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;

import jakarta.servlet.ServletContext;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link NacosWebServerListener} unit tests.
 */
@ExtendWith(MockitoExtension.class)
class NacosWebServerListenerTest {
    
    @Mock
    private ServerMemberManager serverMemberManager;
    
    @Mock
    private ServletContext servletContext;
    
    @Mock
    private WebServerInitializedEvent event;
    
    @Mock
    private WebServerApplicationContext applicationContext;
    
    @Mock
    private org.springframework.boot.web.server.WebServer webServer;
    
    private NacosWebServerListener listener;
    
    @BeforeEach
    void setUp() {
        when(servletContext.getContextPath()).thenReturn("/nacos");
        listener = new NacosWebServerListener(serverMemberManager, servletContext);
    }
    
    @Test
    void constructorSetsContextPath() {
        ServletContext ctx = org.mockito.Mockito.mock(ServletContext.class);
        when(ctx.getContextPath()).thenReturn("/ctx");
        new NacosWebServerListener(serverMemberManager, ctx);
        verify(ctx).getContextPath();
    }
    
    @Test
    void onApplicationEventIgnoresManagementNamespace() {
        when(event.getApplicationContext()).thenReturn(applicationContext);
        when(applicationContext.getServerNamespace()).thenReturn("management");
        
        listener.onApplicationEvent(event);
        
        verify(serverMemberManager, never()).setSelfReady(anyInt());
    }
    
    @Test
    void onApplicationEventCallsSetSelfReadyWithPort() {
        when(event.getApplicationContext()).thenReturn(applicationContext);
        when(applicationContext.getServerNamespace()).thenReturn("");
        when(event.getWebServer()).thenReturn(webServer);
        when(webServer.getPort()).thenReturn(8848);
        
        listener.onApplicationEvent(event);
        
        verify(serverMemberManager).setSelfReady(8848);
    }
}
