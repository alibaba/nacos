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

package com.alibaba.nacos.core.controller;

import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.NodeState;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.env.MockEnvironment;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * {@link NacosClusterController} unit test.
 *
 * @author chenglu
 * @date 2021-07-07 22:53
 */
@RunWith(MockitoJUnitRunner.class)
public class NacosClusterControllerTest {
    
    @InjectMocks
    private NacosClusterController nacosClusterController;
    
    @Mock
    private ServerMemberManager serverMemberManager;
    
    @Before
    public void setUp() {
        EnvUtil.setEnvironment(new MockEnvironment());
    }
    
    @Test
    public void testSelf() {
        Member self = new Member();
        Mockito.when(serverMemberManager.getSelf()).thenReturn(self);
        
        RestResult<Member> result = nacosClusterController.self();
        assertEquals(self, result.getData());
    }
    
    @Test
    public void testListNodes() {
        Member member1 = new Member();
        member1.setIp("1.1.1.1");
        List<Member> members = Arrays.asList(member1);
        Mockito.when(serverMemberManager.allMembers()).thenReturn(members);
        
        RestResult<Collection<Member>> result = nacosClusterController.listNodes("1.1.1.1");
        assertEquals(1, result.getData().size());
    }
    
    @Test
    public void testListSimpleNodes() {
        Mockito.when(serverMemberManager.getMemberAddressInfos()).thenReturn(Collections.singleton("1.1.1.1"));
        
        RestResult<Collection<String>> result = nacosClusterController.listSimpleNodes();
        assertEquals(1, result.getData().size());
    }
    
    @Test
    public void testGetHealth() {
        Member self = new Member();
        self.setState(NodeState.UP);
        Mockito.when(serverMemberManager.getSelf()).thenReturn(self);
        
        RestResult<String> result = nacosClusterController.getHealth();
        assertEquals(NodeState.UP.name(), result.getData());
    }
    
    @Test
    public void testReport() {
        Member self = new Member();
        Mockito.when(serverMemberManager.update(Mockito.any())).thenReturn(true);
        Mockito.when(serverMemberManager.getSelf()).thenReturn(self);
        Member member = new Member();
        member.setIp("1.1.1.1");
        member.setPort(8848);
        member.setAddress("test");
        RestResult<String> result = nacosClusterController.report(member);
        String expected = JacksonUtils.toJson(self);
        assertEquals(expected, result.getData());
    }
    
    @Test
    public void testSwitchLookup() {
        RestResult<String> result = nacosClusterController.switchLookup("test");
        Assert.assertTrue(result.ok());
    }
    
    @Test
    public void testLeave() throws Exception {
        RestResult<String> result = nacosClusterController.leave(Collections.singletonList("1.1.1.1"), true);
        Assert.assertFalse(result.ok());
        assertEquals(405, result.getCode());
    }
}
