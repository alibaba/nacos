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

package com.alibaba.nacos.test.core.cluster;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MembersChangeEvent;
import com.alibaba.nacos.core.cluster.MemberUtil;
import com.alibaba.nacos.core.cluster.NodeState;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.env.Constants;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockServletContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cluster node manages unit tests.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ServerMemberManager_ITCase {
    
    private ServerMemberManager memberManager;
    
    @BeforeClass
    public static void initClass() throws Exception {
        System.setProperty(Constants.NACOS_SERVER_IP, "127.0.0.1");
        System.setProperty("server.port", "8847");
        EnvUtil.setIsStandalone(true);
        EnvUtil.setEnvironment(new StandardEnvironment());
    }
    
    @AfterClass
    public static void destroyClass() {
        System.clearProperty(Constants.NACOS_SERVER_IP);
        System.clearProperty("server.port");
    }
    
    @Before
    public void before() throws Exception {
        memberManager = new ServerMemberManager(new MockServletContext());
    }
    
    @After
    public void after() throws Exception {
        memberManager.shutdown();
    }
    
    @Test
    public void testKisFirst() {
        String firstIp = "127.0.0.1:8847";
        String secondIp = "127.0.0.1:8848";
        String thirdIp = "127.0.0.1:8849";
        
        Map<String, Member> map = new HashMap<>(4);
        map.put(firstIp, Member.builder().ip("127.0.0.1").port(8847).state(NodeState.UP).build());
        map.put(secondIp, Member.builder().ip("127.0.0.1").port(8848).state(NodeState.UP).build());
        map.put(thirdIp, Member.builder().ip("127.0.0.1").port(8849).state(NodeState.UP).build());
        
        List<Member> members = new ArrayList<Member>(map.values());
        Collections.sort(members);
        List<String> ss = MemberUtil.simpleMembers(members);
        
        Assert.assertEquals(ss.get(0), members.get(0).getAddress());
    }
    
    @Test
    public void testMemberChange() throws Exception {
        
        AtomicInteger integer = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
        
        NotifyCenter.registerSubscriber(new Subscriber<MembersChangeEvent>() {
            @Override
            public void onEvent(MembersChangeEvent event) {
                integer.incrementAndGet();
                latch.countDown();
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return MembersChangeEvent.class;
            }
        });
        Collection<Member> members = memberManager.allMembers();
        
        System.out.println(members);
        
        memberManager.memberJoin(members);
        
        members.add(Member.builder().ip("115.159.3.213").port(8848).build());
        
        boolean changed = memberManager.memberJoin(members);
        Assert.assertTrue(changed);
        
        latch.await(10_000L, TimeUnit.MILLISECONDS);
        
        Assert.assertEquals(1, integer.get());
    }
    
    @Test
    public void testMemberHealthCheck() throws Exception {
        AtomicReference<Collection<Member>> healthMembers = new AtomicReference<>();
        CountDownLatch first = new CountDownLatch(1);
        CountDownLatch second = new CountDownLatch(1);
        NotifyCenter.registerSubscriber(new Subscriber<MembersChangeEvent>() {
            @Override
            public void onEvent(MembersChangeEvent event) {
                System.out.println(event);
                healthMembers.set(MemberUtil.selectTargetMembers(event.getMembers(), member -> !NodeState.DOWN.equals(member.getState())));
                if (first.getCount() == 1) {
                    first.countDown();
                    return;
                }
                if (second.getCount() == 1) {
                    second.countDown();
                }
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return MembersChangeEvent.class;
            }
        });
        
        String firstIp = "127.0.0.1:8847";
        String secondIp = "127.0.0.1:8848";
        String thirdIp = "127.0.0.1:8849";
        
        Map<String, Member> map = new HashMap<>(4);
        map.put(firstIp, Member.builder().ip("127.0.0.1").port(8847).state(NodeState.UP).build());
        map.put(secondIp, Member.builder().ip("127.0.0.1").port(8848).state(NodeState.UP).build());
        map.put(thirdIp, Member.builder().ip("127.0.0.1").port(8849).state(NodeState.UP).build());
        
        Set<Member> firstMemberList = new HashSet<>(map.values());
        
        memberManager.memberJoin(map.values());
        
        first.await();
        Set<Member> copy = new HashSet<>(firstMemberList);
        copy.removeAll(healthMembers.get());
        Assert.assertEquals(2, copy.size());
        
        Member member = map.get(firstIp);
        member.setState(NodeState.DOWN);
        Assert.assertTrue(memberManager.update(member));
        
        second.await();
        copy = new HashSet<>(firstMemberList);
        copy.removeAll(healthMembers.get());
        Assert.assertEquals(3, copy.size());
        Assert.assertTrue(copy.contains(map.get(firstIp)));
    }
    
}
