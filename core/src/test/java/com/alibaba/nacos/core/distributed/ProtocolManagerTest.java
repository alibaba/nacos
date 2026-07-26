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

package com.alibaba.nacos.core.distributed;

import com.alibaba.nacos.consistency.Config;
import com.alibaba.nacos.consistency.ap.APProtocol;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberMetaDataConstants;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.MembersChangeEvent;
import com.alibaba.nacos.core.utils.ClassUtils;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mockStatic;

/**
 * {@link ProtocolManager} unit test.
 */
@ExtendWith(MockitoExtension.class)
class ProtocolManagerTest {
    
    @Mock
    private ServerMemberManager memberManager;
    
    private ProtocolManager protocolManager;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new MockEnvironment());
        protocolManager = new ProtocolManager(memberManager);
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(null);
    }
    
    @Test
    void testToApMembersInfo() {
        Member m1 = Member.builder().ip("192.168.1.1").port(8848).build();
        Member m2 = Member.builder().ip("192.168.1.2").port(8848).build();
        Collection<Member> members = Arrays.asList(m1, m2);
        
        Set<String> result = ProtocolManager.toAPMembersInfo(members);
        
        assertEquals(2, result.size());
        assertTrue(result.contains("192.168.1.1:8848"));
        assertTrue(result.contains("192.168.1.2:8848"));
    }
    
    @Test
    void testToApMembersInfoEmpty() {
        Set<String> result = ProtocolManager.toAPMembersInfo(Collections.emptyList());
        assertTrue(result.isEmpty());
    }
    
    @Test
    void testToCpMembersInfo() {
        Member m1 = Member.builder().ip("192.168.1.1").port(8848).build();
        Member m2 = Member.builder().ip("192.168.1.2").port(8849).build();
        Collection<Member> members = Arrays.asList(m1, m2);
        
        Set<String> result = ProtocolManager.toCPMembersInfo(members);
        
        assertEquals(2, result.size());
        assertTrue(result.contains("192.168.1.1:7848"));
        assertTrue(result.contains("192.168.1.2:7849"));
    }
    
    @Test
    void testToCpMembersInfoEmpty() {
        Set<String> result = ProtocolManager.toCPMembersInfo(Collections.emptyList());
        assertTrue(result.isEmpty());
    }
    
    @Test
    void testIsCpInitAndIsApInitBeforeInit() {
        assertFalse(protocolManager.isCpInit());
        assertFalse(protocolManager.isApInit());
    }
    
    @Test
    void testDestroyWithNullProtocols() {
        protocolManager.destroy();
    }
    
    @Test
    void testDestroyWithProtocols() {
        CPProtocol cp = org.mockito.Mockito.mock(CPProtocol.class);
        APProtocol ap = org.mockito.Mockito.mock(APProtocol.class);
        ReflectionTestUtils.setField(protocolManager, "cpProtocol", cp);
        ReflectionTestUtils.setField(protocolManager, "apProtocol", ap);
        ReflectionTestUtils.setField(protocolManager, "cpInit", true);
        ReflectionTestUtils.setField(protocolManager, "apInit", true);
        protocolManager.destroy();
        verify(cp).shutdown();
        verify(ap).shutdown();
    }
    
    @Test
    void testOnEventWithProtocols() throws InterruptedException {
        CPProtocol cp = org.mockito.Mockito.mock(CPProtocol.class);
        APProtocol ap = org.mockito.Mockito.mock(APProtocol.class);
        ReflectionTestUtils.setField(protocolManager, "cpProtocol", cp);
        ReflectionTestUtils.setField(protocolManager, "apProtocol", ap);
        Member m = Member.builder().ip("127.0.0.1").port(8848).build();
        MembersChangeEvent event =
            MembersChangeEvent.builder().members(Collections.singletonList(m)).build();
        protocolManager.onEvent(event);
        Thread.sleep(300);
        verify(ap).memberChange(org.mockito.ArgumentMatchers.anySet());
        verify(cp).memberChange(org.mockito.ArgumentMatchers.anySet());
    }
    
    @Test
    void testGetApProtocolWithMockContext() {
        ApplicationContext originalContext = ApplicationUtils.getApplicationContext();
        ConfigurableApplicationContext mockContext = mock(ConfigurableApplicationContext.class);
        APProtocol mockAp = mock(APProtocol.class);
        Config mockConfig = mock(Config.class);
        Member self = Member.builder().ip("127.0.0.1").port(8848).build();
        when(memberManager.getSelf()).thenReturn(self);
        when(memberManager.allMembers()).thenReturn(Collections.singletonList(self));
        when(mockContext.getBean(APProtocol.class)).thenReturn(mockAp);
        when(mockContext.getBean(Config.class)).thenReturn(mockConfig);
        try {
            ApplicationUtils.injectContext(mockContext);
            try (MockedStatic<ClassUtils> classUtilsMock = mockStatic(ClassUtils.class)) {
                classUtilsMock.when(() -> ClassUtils.resolveGenericType(any(Class.class)))
                    .thenReturn((Class) Config.class);
                APProtocol result = protocolManager.getApProtocol();
                assertNotNull(result);
                assertSame(mockAp, result);
                verify(mockAp).init(mockConfig);
                assertTrue(protocolManager.isApInit());
            }
        } finally {
            ApplicationUtils.injectContext(
                originalContext != null ? (ConfigurableApplicationContext) originalContext : null);
        }
    }
    
    @Test
    void testGetCpProtocolWithMockContext() {
        ApplicationContext originalContext = ApplicationUtils.getApplicationContext();
        ConfigurableApplicationContext mockContext = mock(ConfigurableApplicationContext.class);
        CPProtocol mockCp = mock(CPProtocol.class);
        Config mockConfig = mock(Config.class);
        Member self = Member.builder().ip("127.0.0.1").port(8848)
            .extendInfo(Collections.singletonMap(MemberMetaDataConstants.RAFT_PORT, "7848"))
            .build();
        when(memberManager.getSelf()).thenReturn(self);
        when(memberManager.allMembers()).thenReturn(Collections.singletonList(self));
        when(mockContext.getBean(CPProtocol.class)).thenReturn(mockCp);
        when(mockContext.getBean(Config.class)).thenReturn(mockConfig);
        try {
            ApplicationUtils.injectContext(mockContext);
            try (MockedStatic<ClassUtils> classUtilsMock = mockStatic(ClassUtils.class)) {
                classUtilsMock.when(() -> ClassUtils.resolveGenericType(any(Class.class)))
                    .thenReturn((Class) Config.class);
                CPProtocol result = protocolManager.getCpProtocol();
                assertNotNull(result);
                assertSame(mockCp, result);
                verify(mockCp).init(mockConfig);
                assertTrue(protocolManager.isCpInit());
            }
        } finally {
            ApplicationUtils.injectContext(
                originalContext != null ? (ConfigurableApplicationContext) originalContext : null);
        }
    }
}
