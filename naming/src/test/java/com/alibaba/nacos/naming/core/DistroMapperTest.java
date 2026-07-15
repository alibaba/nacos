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

package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.api.common.NodeState;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MembersChangeEvent;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.StandardEnvironment;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.ConcurrentSkipListMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DistroMapperTest {
    
    private DistroMapper distroMapper;
    
    @Mock
    private ServerMemberManager memberManager;
    
    private SwitchDomain switchDomain;
    
    private String serviceName = "com.taobao.service";
    
    private String ip1 = "1.1.1.1";
    
    private String ip2 = "2.2.2.2";
    
    private String ip3 = "3.3.3.3";
    
    private String ip4 = "4.4.4.4";
    
    private int port = 8848;
    
    @BeforeEach
    void setUp() {
        ConcurrentSkipListMap<String, Member> serverList = new ConcurrentSkipListMap<>();
        EnvUtil.setEnvironment(new StandardEnvironment());
        EnvUtil.setIsStandalone(true);
        serverList.put(ip1, Member.builder().ip(ip1).port(port).build());
        serverList.put(ip2, Member.builder().ip(ip2).port(port).build());
        serverList.put(ip3, Member.builder().ip(ip3).port(port).build());
        EnvUtil.setLocalAddress(ip4);
        serverList.put(EnvUtil.getLocalAddress(),
            Member.builder().ip(EnvUtil.getLocalAddress()).port(port).build());
        HashSet<Member> set = new HashSet<>(serverList.values());
        switchDomain = new SwitchDomain();
        distroMapper = new DistroMapper(memberManager, switchDomain);
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setIsStandalone(null);
    }
    
    @Test
    void testResponsible() {
        assertTrue(distroMapper.responsible(serviceName));
    }
    
    @Test
    void testResponsibleReturnsFalseWhenHealthyListEmpty() {
        EnvUtil.setIsStandalone(false);
        
        assertFalse(distroMapper.responsible(serviceName));
    }
    
    @Test
    void testResponsibleReturnsTrueWhenLocalAddressMissing() {
        EnvUtil.setIsStandalone(false);
        EnvUtil.setLocalAddress(ip4 + ":" + port);
        distroMapper.onEvent(MembersChangeEvent.builder()
            .members(Arrays.asList(member(ip1, NodeState.UP), member(ip2, NodeState.UP))).build());
        
        assertTrue(distroMapper.responsible(serviceName));
    }
    
    @Test
    void testResponsibleUsesHashRange() {
        EnvUtil.setIsStandalone(false);
        EnvUtil.setLocalAddress(ip2 + ":" + port);
        distroMapper.onEvent(MembersChangeEvent.builder()
            .members(Arrays.asList(member(ip1, NodeState.UP), member(ip2, NodeState.UP),
                member(ip3, NodeState.UP)))
            .build());
        
        assertTrue(distroMapper.responsible(tagForIndex(1, distroMapper.getHealthyList().size())));
        assertFalse(distroMapper.responsible(tagForIndex(0, distroMapper.getHealthyList().size())));
    }
    
    @Test
    void testMapSrv() {
        String server = distroMapper.mapSrv(serviceName);
        assertEquals(server, ip4);
    }
    
    @Test
    void testInitLoadsMembers() {
        when(memberManager.allMembers()).thenReturn(Arrays.asList(member(ip2, NodeState.UP),
            member(ip1, NodeState.UP)));
        
        try {
            distroMapper.init();
            
            assertEquals(Arrays.asList(ip1 + ":" + port, ip2 + ":" + port),
                distroMapper.getHealthyList());
        } finally {
            NotifyCenter.deregisterSubscriber(distroMapper);
        }
    }
    
    @Test
    void testMapSrvSelectsHealthyServer() {
        EnvUtil.setIsStandalone(false);
        distroMapper.onEvent(MembersChangeEvent.builder()
            .members(Arrays.asList(member(ip1, NodeState.UP), member(ip2, NodeState.UP),
                member(ip3, NodeState.UP)))
            .build());
        
        String server = distroMapper.mapSrv(serviceName);
        
        assertTrue(distroMapper.getHealthyList().contains(server));
    }
    
    @Test
    void testMapSrvReturnsLocalWhenDistroDisabled() {
        EnvUtil.setLocalAddress(ip4 + ":" + port);
        switchDomain.setDistroEnabled(false);
        
        assertEquals(ip4 + ":" + port, distroMapper.mapSrv(serviceName));
    }
    
    @Test
    void testMapSrvReturnsLocalWhenHashFails() {
        EnvUtil.setLocalAddress(ip4 + ":" + port);
        distroMapper.onEvent(MembersChangeEvent.builder()
            .members(Collections.singletonList(member(ip1, NodeState.UP))).build());
        
        assertEquals(ip4 + ":" + port, distroMapper.mapSrv(null));
    }
    
    @Test
    void testOnEventKeepsUpAndSuspiciousMembersSorted() {
        distroMapper.onEvent(MembersChangeEvent.builder()
            .members(Arrays.asList(member(ip3, NodeState.SUSPICIOUS), member(ip2, NodeState.DOWN),
                member(ip1, NodeState.UP)))
            .build());
        
        assertEquals(Arrays.asList(ip1 + ":" + port, ip3 + ":" + port),
            distroMapper.getHealthyList());
    }
    
    @Test
    void testIgnoreExpireEvent() {
        assertTrue(distroMapper.ignoreExpireEvent());
    }
    
    private Member member(String ip, NodeState state) {
        return Member.builder().ip(ip).port(port).state(state).build();
    }
    
    private String tagForIndex(int targetIndex, int size) {
        for (int i = 0; i < 1000; i++) {
            String tag = "tag" + i;
            int actualIndex = Math.abs(tag.hashCode() % Integer.MAX_VALUE) % size;
            if (actualIndex == targetIndex) {
                return tag;
            }
        }
        throw new IllegalStateException("cannot find target tag");
    }
}
