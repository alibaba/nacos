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

package com.alibaba.nacos.console.cluster;

import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberLookup;
import com.alibaba.nacos.core.cluster.MembersChangeEvent;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class RemoteServerMemberManagerTest {
    
    private ConfigurableEnvironment cachedEnvironment;
    
    private RemoteServerMemberManager memberManager;
    
    @Mock
    private MemberLookup mockLookup;
    
    @BeforeEach
    void setUp() throws Exception {
        cachedEnvironment = EnvUtil.getEnvironment();
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_ADMIN_ENABLED, "false");
        EnvUtil.setEnvironment(environment);
        memberManager = new RemoteServerMemberManager();
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(cachedEnvironment);
    }
    
    @Test
    void testInitAndStartLookup() throws Exception {
        memberManager.init();
        MemberLookup lookup = (MemberLookup) ReflectionTestUtils.getField(memberManager, "lookup");
        assertNotNull(lookup);
    }
    
    @Test
    void testMemberChange() {
        // 准备测试数据
        Member member = Member.builder().ip("127.0.0.1").port(8848).build();
        Set<Member> members = Collections.singleton(member);
        
        // 执行方法
        boolean result = memberManager.memberChange(members);
        
        // 验证结果
        assertTrue(result);
        assertEquals(1, memberManager.allMembers().size());
        assertEquals(member, memberManager.allMembers().iterator().next());
    }
    
    @Test
    void testAllMembersReturnsCopy() {
        // 准备测试数据
        Member member = Member.builder().ip("127.0.0.1").port(8848).build();
        memberManager.memberChange(Collections.singleton(member));
        
        // 修改返回的集合
        Collection<Member> members = memberManager.allMembers();
        members.clear();
        
        // 验证原始数据未被修改
        assertEquals(1, memberManager.allMembers().size());
    }
    
    @Test
    void testEventPublishingOnMemberChange() {
        try (MockedStatic<NotifyCenter> notifyCenter = Mockito.mockStatic(NotifyCenter.class)) {
            // 准备测试数据
            Member member = Member.builder().ip("127.0.0.1").port(8848).build();
            
            // 执行方法
            memberManager.memberChange(Collections.singleton(member));
            
            // 验证事件发布
            ArgumentCaptor<MembersChangeEvent> eventCaptor = ArgumentCaptor.forClass(MembersChangeEvent.class);
            notifyCenter.verify(() -> NotifyCenter.publishEvent(eventCaptor.capture()));
            
            MembersChangeEvent event = eventCaptor.getValue();
            assertEquals(1, event.getMembers().size());
            assertEquals(member, event.getMembers().iterator().next());
        }
    }
    
    @Test
    void shouldHandleEmptyMemberList() {
        boolean result = memberManager.memberChange(Collections.emptySet());
        assertTrue(result);
        assertEquals(0, memberManager.allMembers().size());
    }
    
    @Test
    void shouldHandleDuplicateMembers() {
        Member member1 = Member.builder().ip("127.0.0.1").port(8848).build();
        Member member2 = Member.builder().ip("127.0.0.1").port(8848).build();
        
        memberManager.memberChange(List.of(member1, member2));
        
        assertEquals(1, memberManager.allMembers().size());
    }
}