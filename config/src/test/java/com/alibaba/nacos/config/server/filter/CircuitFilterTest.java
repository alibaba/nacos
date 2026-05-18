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

package com.alibaba.nacos.config.server.filter;

import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.Observable;
import com.alibaba.nacos.common.utils.Observer;
import com.alibaba.nacos.config.server.model.event.RaftDbErrorRecoverEvent;
import com.alibaba.nacos.consistency.ProtocolMetaData;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberMetaDataConstants;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.persistence.model.event.RaftDbErrorEvent;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CircuitFilterTest {
    
    private CircuitFilter filter;
    
    @Mock
    private ServerMemberManager memberManager;
    
    @Mock
    private CPProtocol protocol;
    
    @Mock
    private HttpServletRequest request;
    
    @Mock
    private HttpServletResponse response;
    
    @Mock
    private FilterChain chain;
    
    @Mock
    private ProtocolMetaData protocolMetaData;
    
    @BeforeEach
    void setUp() throws Exception {
        filter = new CircuitFilter();
        injectField(filter, "memberManager", memberManager);
        injectField(filter, "protocol", protocol);
        injectField(filter, "isOpenService", true);
        injectField(filter, "isDowngrading", false);
    }
    
    @Test
    void testSecurityExceptionReturns403() throws Exception {
        doThrow(new SecurityException("access denied")).when(chain)
            .doFilter(request, response);
        
        filter.doFilter(request, response, chain);
        
        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN),
            contains("access denied"));
    }
    
    @Test
    void testSecurityExceptionFromDownstreamAuthFilterReturns403() throws Exception {
        doThrow(new SecurityException("Authority validation failed")).when(chain)
            .doFilter(request, response);
        
        filter.doFilter(request, response, chain);
        
        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN),
            contains("Authority validation failed"));
    }
    
    @Test
    void testServiceNotOpenReturns503() throws Exception {
        injectField(filter, "isOpenService", false);
        
        filter.doFilter(request, response, chain);
        
        verify(response).sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
            "In the node initialization, unable to process any requests at this time");
        verify(chain, never()).doFilter(request, response);
    }
    
    @Test
    void testDowngradingReturns503() throws Exception {
        injectField(filter, "isDowngrading", true);
        
        filter.doFilter(request, response, chain);
        
        verify(response).sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
            "Unable to process the request at this time: System triggered degradation");
        verify(chain, never()).doFilter(request, response);
    }
    
    @Test
    void testGenericExceptionReturns500() throws Exception {
        doThrow(new RuntimeException("internal error")).when(chain)
            .doFilter(request, response);
        
        filter.doFilter(request, response, chain);
        
        verify(response).sendError(eq(HttpServletResponse.SC_INTERNAL_SERVER_ERROR),
            contains("internal error"));
    }
    
    @Test
    void testNormalRequestPassesThrough() throws Exception {
        filter.doFilter(request, response, chain);
        
        verify(chain).doFilter(request, response);
        verify(response, never()).sendError(eq(HttpServletResponse.SC_FORBIDDEN),
            contains("access denied"));
    }
    
    @Test
    void testDestroyDoesNotThrow() {
        assertDoesNotThrow(() -> filter.destroy());
    }
    
    @Test
    void testInitListenerSelfInCluster() throws Exception {
        MockedStatic<EnvUtil> envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        try {
            envUtilMockedStatic.when(() -> EnvUtil.getProperty(anyString(), anyString()))
                .thenReturn("unknown");
            when(protocol.protocolMetaData()).thenReturn(protocolMetaData);
            ArgumentCaptor<Observer> observerCaptor =
                ArgumentCaptor.forClass(Observer.class);
            
            Member self = new Member();
            self.setIp("127.0.0.1");
            self.setExtendVal(MemberMetaDataConstants.RAFT_PORT, "7848");
            when(memberManager.getSelf()).thenReturn(self);
            
            filter.init();
            
            verify(protocolMetaData).subscribe(anyString(), anyString(),
                observerCaptor.capture());
            Observer observer = observerCaptor.getValue();
            
            ProtocolMetaData.ValueItem valueItem =
                new ProtocolMetaData.ValueItem("test");
            Field dataField =
                ProtocolMetaData.ValueItem.class.getDeclaredField("data");
            dataField.setAccessible(true);
            dataField.set(valueItem,
                Arrays.asList("127.0.0.1:7848", "127.0.0.2:7848"));
            
            observer.update(valueItem);
            
            filter.doFilter(request, response, chain);
            verify(chain).doFilter(request, response);
        } finally {
            envUtilMockedStatic.close();
        }
    }
    
    @Test
    void testInitListenerEmptyPeers() throws Exception {
        when(protocol.protocolMetaData()).thenReturn(protocolMetaData);
        ArgumentCaptor<Observer> observerCaptor = ArgumentCaptor.forClass(Observer.class);
        
        filter.init();
        
        verify(protocolMetaData).subscribe(anyString(), anyString(),
            observerCaptor.capture());
        Observer observer = observerCaptor.getValue();
        
        ProtocolMetaData.ValueItem valueItem =
            new ProtocolMetaData.ValueItem("test");
        Field dataField =
            ProtocolMetaData.ValueItem.class.getDeclaredField("data");
        dataField.setAccessible(true);
        dataField.set(valueItem, Collections.emptyList());
        
        observer.update(valueItem);
        
        filter.doFilter(request, response, chain);
        verify(response).sendError(eq(HttpServletResponse.SC_SERVICE_UNAVAILABLE),
            contains("node initialization"));
    }
    
    @Test
    void testInitListenerSelfNotInCluster() throws Exception {
        MockedStatic<EnvUtil> envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        try {
            envUtilMockedStatic.when(() -> EnvUtil.getProperty(anyString(), anyString()))
                .thenReturn("unknown");
            when(protocol.protocolMetaData()).thenReturn(protocolMetaData);
            ArgumentCaptor<Observer> observerCaptor =
                ArgumentCaptor.forClass(Observer.class);
            
            Member self = new Member();
            self.setIp("127.0.0.1");
            self.setExtendVal(MemberMetaDataConstants.RAFT_PORT, "7848");
            when(memberManager.getSelf()).thenReturn(self);
            
            filter.init();
            
            verify(protocolMetaData).subscribe(anyString(), anyString(),
                observerCaptor.capture());
            Observer observer = observerCaptor.getValue();
            
            ProtocolMetaData.ValueItem valueItem =
                new ProtocolMetaData.ValueItem("test");
            Field dataField =
                ProtocolMetaData.ValueItem.class.getDeclaredField("data");
            dataField.setAccessible(true);
            dataField.set(valueItem, Arrays.asList("127.0.0.2:7848"));
            
            observer.update(valueItem);
            
            filter.doFilter(request, response, chain);
            verify(response).sendError(eq(HttpServletResponse.SC_SERVICE_UNAVAILABLE),
                contains("node initialization"));
        } finally {
            envUtilMockedStatic.close();
        }
    }
    
    @Test
    void testInitListenerNonValueItemIgnored() throws Exception {
        when(protocol.protocolMetaData()).thenReturn(protocolMetaData);
        ArgumentCaptor<Observer> observerCaptor = ArgumentCaptor.forClass(Observer.class);
        
        injectField(filter, "isOpenService", false);
        
        filter.init();
        
        verify(protocolMetaData).subscribe(anyString(), anyString(),
            observerCaptor.capture());
        Observer observer = observerCaptor.getValue();
        
        observer.update(new Observable() {
        });
        
        filter.doFilter(request, response, chain);
        verify(response).sendError(eq(HttpServletResponse.SC_SERVICE_UNAVAILABLE),
            contains("node initialization"));
    }
    
    @Test
    void testRegisterSubscribeRaftDbErrorEvent() throws Exception {
        when(protocol.protocolMetaData()).thenReturn(protocolMetaData);
        injectField(filter, "isOpenService", true);
        injectField(filter, "isDowngrading", false);
        
        filter.init();
        
        NotifyCenter.publishEvent(new RaftDbErrorEvent(new RuntimeException("db err")));
        Thread.sleep(2000);
        
        filter.doFilter(request, response, chain);
        verify(response).sendError(eq(HttpServletResponse.SC_SERVICE_UNAVAILABLE),
            contains("degradation"));
    }
    
    @Test
    void testRegisterSubscribeRaftDbRecoverEvent() throws Exception {
        when(protocol.protocolMetaData()).thenReturn(protocolMetaData);
        injectField(filter, "isOpenService", true);
        injectField(filter, "isDowngrading", true);
        
        filter.init();
        
        NotifyCenter.publishEvent(new RaftDbErrorRecoverEvent());
        Thread.sleep(500);
        
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }
    
    private static void injectField(Object target, String fieldName, Object value)
        throws Exception {
        Field field = CircuitFilter.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
