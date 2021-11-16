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
import com.alibaba.nacos.sys.utils.InetUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import javax.servlet.ServletContext;
import java.util.Collections;
import java.util.List;
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
    
    @Mock
    private WebServerInitializedEvent mockEvent;
    
    private ServerMemberManager serverMemberManager;
    
    private static final AtomicBoolean EVENT_PUBLISH = new AtomicBoolean(false);
    
    @Before
    public void setUp() throws Exception {
        when(environment.getProperty("server.port", Integer.class, 8848)).thenReturn(8848);
        when(environment.getProperty("nacos.member-change-event.queue.size", Integer.class, 128)).thenReturn(128);
        EnvUtil.setEnvironment(environment);
        EnvUtil.setIsStandalone(true);
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
    public void testInit() {
        String selfIp = InetUtils.getSelfIP();
        Member member = serverMemberManager.getSelf();
        assertEquals(selfIp, member.getIp());
        assertTrue(member.getAbilities().getRemoteAbility().isSupportRemoteConnection());
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
    
    @Test
    public void testHasMember() {
        assertTrue(serverMemberManager.hasMember("1.1.1.1"));
    }
    
    @Test
    public void testMemberLeave() {
        Member member = Member.builder().ip("1.1.3.3").port(8848).state(NodeState.DOWN).build();
        boolean joinResult = serverMemberManager.memberJoin(Collections.singletonList(member));
        assertTrue(joinResult);
    
        List<String> ips = serverMemberManager.getServerListUnhealth();
        assertEquals(1, ips.size());
        
        boolean result = serverMemberManager.memberLeave(Collections.singletonList(member));
        assertTrue(result);
    }
    
    @Test
    public void testIsUnHealth() {
        assertFalse(serverMemberManager.isUnHealth("1.1.1.1"));
    }
    
    @Test
    public void testIsFirstIp() {
        assertFalse(serverMemberManager.isFirstIp());
    }
    
    @Test
    public void testGetServerList() {
        assertEquals(2, serverMemberManager.getServerList().size());
    }
    
    @Test
    public void testEnvSetPort() {
        ServletWebServerApplicationContext context = new ServletWebServerApplicationContext();
        context.setServerNamespace("management");
        Mockito.when(mockEvent.getApplicationContext()).thenReturn(context);
        serverMemberManager.onApplicationEvent(mockEvent);
        int port = EnvUtil.getPort();
        Assert.assertEquals(port, 8848);
    }
}
