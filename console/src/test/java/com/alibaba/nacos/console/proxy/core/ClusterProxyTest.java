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

package com.alibaba.nacos.console.proxy.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.response.NacosMember;
import com.alibaba.nacos.console.handler.core.ClusterHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
public class ClusterProxyTest {
    
    @Mock
    private ClusterHandler clusterHandler;
    
    private ClusterProxy clusterProxy;
    
    @BeforeEach
    public void setUp() {
        clusterProxy = new ClusterProxy(clusterHandler);
    }
    
    @Test
    public void getNodeListWithBlankIpKeyWord() throws NacosException {
        Collection<NacosMember> mockMembers = new ArrayList<>();
        NacosMember member1 = new NacosMember();
        member1.setAddress("192.168.1.1");
        NacosMember member2 = new NacosMember();
        member2.setAddress("192.168.1.2");
        mockMembers.add(member1);
        mockMembers.add(member2);
        
        doReturn(mockMembers).when(clusterHandler).getNodeList("");
        Collection<NacosMember> result = clusterProxy.getNodeList("");
        
        assertEquals(2, result.size());
        assertTrue(result.contains(member1));
        assertTrue(result.contains(member2));
    }
    
    @Test
    public void getNodeListWithMatchingIpKeyWord() throws NacosException {
        NacosMember member1 = new NacosMember();
        member1.setAddress("192.168.1.1");
        NacosMember member2 = new NacosMember();
        member2.setAddress("192.168.1.2");
        NacosMember member3 = new NacosMember();
        member3.setAddress("192.168.2.1");
        Collection<NacosMember> mockMembers = new ArrayList<>();
        mockMembers.add(member1);
        mockMembers.add(member2);
        mockMembers.add(member3);
        
        doReturn(mockMembers).when(clusterHandler).getNodeList("192.168.1");
        Collection<NacosMember> result = clusterProxy.getNodeList("192.168.1");
        
        assertEquals(2, result.size());
        assertTrue(result.contains(member1));
        assertTrue(result.contains(member2));
    }
}
