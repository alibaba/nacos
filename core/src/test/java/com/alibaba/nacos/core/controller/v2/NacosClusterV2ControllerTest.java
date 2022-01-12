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

import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.NodeState;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.model.request.LookupUpdateRequest;
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

@RunWith(MockitoJUnitRunner.class)
public class NacosClusterV2ControllerTest {
    
    @InjectMocks
    private NacosClusterV2Controller nacosClusterV2Controller;
    
    @Mock
    private ServerMemberManager serverMemberManager;
    
    private final MockEnvironment environment = new MockEnvironment();
    
    @Before
    public void setUp() {
        EnvUtil.setEnvironment(environment);
    }
    
    @Test
    public void testSelf() {
        Member self = new Member();
        Mockito.when(serverMemberManager.getSelf()).thenReturn(self);
        
        RestResult<Member> result = nacosClusterV2Controller.self();
        Assert.assertTrue(result.ok());
        Assert.assertEquals(self, result.getData());
    }
    
    @Test
    public void testListNodes() {
        Member member1 = new Member();
        member1.setIp("1.1.1.1");
        member1.setPort(8848);
        member1.setState(NodeState.DOWN);
        Member member2 = new Member();
        member2.setIp("2.2.2.2");
        member2.setPort(8848);
        
        List<Member> members = Arrays.asList(member1, member2);
        Mockito.when(serverMemberManager.allMembers()).thenReturn(members);
        
        RestResult<Collection<Member>> result1 = nacosClusterV2Controller.listNodes("1.1.1.1", null);
        Assert.assertTrue(result1.getData().stream().findFirst().isPresent());
        Assert.assertEquals("1.1.1.1:8848", result1.getData().stream().findFirst().get().getAddress());
    
        RestResult<Collection<Member>> result2 = nacosClusterV2Controller.listNodes(null, "up");
        Assert.assertTrue(result2.getData().stream().findFirst().isPresent());
        Assert.assertEquals("2.2.2.2:8848", result2.getData().stream().findFirst().get().getAddress());
    }
    
    @Test
    public void testUpdate() {
        Mockito.when(serverMemberManager.update(Mockito.any())).thenReturn(true);
        
        Member member = new Member();
        member.setIp("1.1.1.1");
        member.setPort(8848);
        member.setAddress("test");
        RestResult<Void> result = nacosClusterV2Controller.updateNodes(Collections.singletonList(member));
        Assert.assertTrue(result.ok());
    }
    
    @Test
    public void testSwitchLookup() {
        LookupUpdateRequest request = new LookupUpdateRequest();
        request.setType("test");
        
        RestResult<Void> result = nacosClusterV2Controller.updateLookup(request);
        Assert.assertTrue(result.ok());
    }

    @Test
    public void testLeave() throws Exception {
        RestResult<Void> result = nacosClusterV2Controller.deleteNodes(Collections.singletonList("1.1.1.1"));
        Assert.assertTrue(result.ok());
    }
}
