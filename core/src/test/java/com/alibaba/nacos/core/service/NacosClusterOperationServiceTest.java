/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.service;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.NodeState;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.model.request.LookupUpdateRequest;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NacosClusterOperationTest.
 *
 * @author dongyafei
 * @date 2022/8/15
 */

@ExtendWith(MockitoExtension.class)
class NacosClusterOperationServiceTest {
    
    @Mock
    private final MockEnvironment environment = new MockEnvironment();
    
    private NacosClusterOperationService nacosClusterOperationService;
    
    @Mock
    private ServerMemberManager serverMemberManager;
    
    @BeforeEach
    void setUp() throws Exception {
        this.nacosClusterOperationService = new NacosClusterOperationService(serverMemberManager);
        EnvUtil.setEnvironment(environment);
    }
    
    @Test
    void testSelf() {
        Member member = new Member();
        member.setIp("1.1.1.1");
        member.setPort(8848);
        member.setState(NodeState.UP);
        
        when(serverMemberManager.getSelf()).thenReturn(member);
        
        Member result = nacosClusterOperationService.self();
        assertEquals("1.1.1.1:8848", result.getAddress());
    }
    
    @Test
    void testListNodes() throws NacosException {
        Member member1 = new Member();
        member1.setIp("1.1.1.1");
        member1.setPort(8848);
        member1.setState(NodeState.DOWN);
        Member member2 = new Member();
        member2.setIp("2.2.2.2");
        member2.setPort(8848);
        List<Member> members = Arrays.asList(member1, member2);
        
        when(serverMemberManager.allMembers()).thenReturn(members);
        
        Collection<Member> result1 = nacosClusterOperationService.listNodes("1.1.1.1", null);
        assertTrue(result1.stream().findFirst().isPresent());
        assertEquals("1.1.1.1:8848", result1.stream().findFirst().get().getAddress());
        
        Collection<Member> result2 = nacosClusterOperationService.listNodes(null, NodeState.UP);
        assertTrue(result2.stream().findFirst().isPresent());
        assertEquals("2.2.2.2:8848", result2.stream().findFirst().get().getAddress());
    }
    
    @Test
    void testSelfHealth() {
        Member member = new Member();
        member.setIp("1.1.1.1");
        member.setPort(8848);
        member.setState(NodeState.UP);
        
        when(serverMemberManager.getSelf()).thenReturn(member);
        
        String health = nacosClusterOperationService.selfHealth();
        assertEquals(NodeState.UP.name(), health);
    }
    
    @Test
    void testUpdateNodes() {
        Member member1 = new Member();
        member1.setIp("1.1.1.1");
        member1.setAddress("test");
        member1.setPort(8848);
        member1.setState(NodeState.DOWN);
        Member member2 = new Member();
        member2.setIp("2.2.2.2");
        member2.setPort(8848);
        List<Member> members = Arrays.asList(member1, member2);
        
        when(serverMemberManager.update(any())).thenReturn(true);
        Boolean result = nacosClusterOperationService.updateNodes(members);
        verify(serverMemberManager, times(1)).update(any());
        assertTrue(result);
    }
    
    @Test
    void testUpdateLookup() throws NacosException {
        LookupUpdateRequest lookupUpdateRequest = new LookupUpdateRequest();
        lookupUpdateRequest.setType("test");
        Boolean result = nacosClusterOperationService.updateLookup(lookupUpdateRequest);
        verify(serverMemberManager).switchLookup("test");
        assertTrue(result);
    }
}
