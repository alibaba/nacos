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

package com.alibaba.nacos.core.cluster;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.notify.EventPublisher;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.ConfigurableEnvironment;

import javax.servlet.ServletContext;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ServerMemberManagerTest {
    
    @Mock
    private ConfigurableEnvironment environment;
    
    @Mock
    private ServletContext servletContext;
    
    @Mock
    private EventPublisher eventPublisher;
    
    private ServerMemberManager serverMemberManager;
    
    private static final AtomicBoolean EVENT_PUBLISH = new AtomicBoolean(false);
    
    @Before
    public void setUp() throws Exception {
        when(environment.getProperty("server.port", Integer.class, 8848)).thenReturn(8848);
        when(environment.getProperty("nacos.member-change-event.queue.size", Integer.class, 128)).thenReturn(128);
        EnvUtil.setEnvironment(environment);
        when(servletContext.getContextPath()).thenReturn("");
        serverMemberManager = new ServerMemberManager(servletContext);
        serverMemberManager.updateMember(Member.builder().ip("1.1.1.1").port(8848).state(NodeState.UP).build());
        serverMemberManager.getMemberAddressInfos().add("1.1.1.1:8848");
        NotifyCenter.getPublisherMap().put(MembersChangeEvent.class.getCanonicalName(), eventPublisher);
    }
    
    @After
    public void tearDown() throws NacosException {
        EVENT_PUBLISH.set(false);
        NotifyCenter.deregisterPublisher(MembersChangeEvent.class);
        serverMemberManager.shutdown();
    }
    
    @Test
    public void testUpdateNonExistMember() {
        Member newMember = Member.builder().ip("1.1.1.2").port(8848).state(NodeState.UP).build();
        assertFalse(serverMemberManager.update(newMember));
    }
    
    @Test
    public void testUpdateDownMember() {
        Member newMember = Member.builder().ip("1.1.1.1").port(8848).state(NodeState.DOWN).build();
        assertTrue(serverMemberManager.update(newMember));
        assertFalse(serverMemberManager.getMemberAddressInfos().contains("1.1.1.1:8848"));
        verify(eventPublisher).publish(any(MembersChangeEvent.class));
    }
    
    @Test
    public void testUpdateVersionMember() {
        Member newMember = Member.builder().ip("1.1.1.1").port(8848).state(NodeState.UP).build();
        newMember.setExtendVal(MemberMetaDataConstants.VERSION, "testVersion");
        assertTrue(serverMemberManager.update(newMember));
        assertTrue(serverMemberManager.getMemberAddressInfos().contains("1.1.1.1:8848"));
        assertEquals("testVersion",
                serverMemberManager.getServerList().get("1.1.1.1:8848").getExtendVal(MemberMetaDataConstants.VERSION));
        verify(eventPublisher).publish(any(MembersChangeEvent.class));
    }
    
    @Test
    public void testUpdateNonBasicExtendInfoMember() {
        Member newMember = Member.builder().ip("1.1.1.1").port(8848).state(NodeState.UP).build();
        newMember.setExtendVal("naming", "test");
        assertTrue(serverMemberManager.update(newMember));
        assertTrue(serverMemberManager.getMemberAddressInfos().contains("1.1.1.1:8848"));
        assertEquals("test", serverMemberManager.getServerList().get("1.1.1.1:8848").getExtendVal("naming"));
        verify(eventPublisher, never()).publish(any(MembersChangeEvent.class));
    }
}
