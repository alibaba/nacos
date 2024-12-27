/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.core.controller.v2;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.NodeState;
import com.alibaba.nacos.core.model.request.LookupUpdateRequest;
import com.alibaba.nacos.core.service.NacosClusterOperationService;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NacosClusterControllerV2Test {
    
    private final MockEnvironment environment = new MockEnvironment();
    
    @InjectMocks
    private NacosClusterControllerV2 nacosClusterControllerV2;
    
    @Mock
    private NacosClusterOperationService nacosClusterOperationService;
    
    @BeforeEach
    void setUp() {
        nacosClusterControllerV2 = new NacosClusterControllerV2(nacosClusterOperationService);
        EnvUtil.setEnvironment(environment);
    }
    
    @Test
    void testSelf() {
        Member self = new Member();
        when(nacosClusterOperationService.self()).thenReturn(self);
        
        Result<Member> result = nacosClusterControllerV2.self();
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(self, result.getData());
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
        Mockito.when(nacosClusterOperationService.listNodes(any(), any())).thenReturn(members);
        
        Result<Collection<Member>> result = nacosClusterControllerV2.listNodes("1.1.1.1", null);
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData().stream().findFirst().isPresent());
        assertEquals("1.1.1.1:8848", result.getData().stream().findFirst().get().getAddress());
    }
    
    @Test
    void testSelfHealth() {
        String selfHealth = "UP";
        when(nacosClusterOperationService.selfHealth()).thenReturn(selfHealth);
        
        Result<String> result = nacosClusterControllerV2.selfHealth();
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(selfHealth, result.getData());
    }
    
    @Test
    void testUpdate() throws NacosApiException {
        Member member = new Member();
        member.setIp("1.1.1.1");
        member.setPort(8848);
        member.setAddress("test");
        when(nacosClusterOperationService.updateNodes(any())).thenReturn(true);
        Result<Boolean> result = nacosClusterControllerV2.updateNodes(Collections.singletonList(member));
        verify(nacosClusterOperationService).updateNodes(any());
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData());
    }
    
    @Test
    void testSwitchLookup() throws NacosException {
        LookupUpdateRequest request = new LookupUpdateRequest();
        request.setType("test");
        
        when(nacosClusterOperationService.updateLookup(any())).thenReturn(true);
        Result<Boolean> result = nacosClusterControllerV2.updateLookup(request);
        verify(nacosClusterOperationService).updateLookup(any());
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData());
    }
    
    @Test
    void testLeave() throws Exception {
        RestResult<Void> result = nacosClusterControllerV2.deleteNodes(Collections.singletonList("1.1.1.1"));
        assertFalse(result.ok());
        assertEquals(405, result.getCode());
    }
}
