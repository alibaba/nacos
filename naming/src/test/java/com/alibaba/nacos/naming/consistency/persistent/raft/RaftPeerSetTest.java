/*
 *  Copyright 1999-2018 Alibaba Group Holding Ltd.
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
 */

package com.alibaba.nacos.naming.consistency.persistent.raft;

import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockServletContext;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

public class RaftPeerSetTest {
    
    @BeforeClass
    public static void beforeClass() {
        ApplicationUtils.injectContext(new StaticApplicationContext());
        EnvUtil.setEnvironment(new MockEnvironment());
    }
    
    private ServerMemberManager memberManager;
    
    @Before
    public void before() throws Exception {
        memberManager = new ServerMemberManager(new MockServletContext());
    }
    
    @Test
    public void testRaftPeerChange() {
        final AtomicBoolean notifyReceived = new AtomicBoolean(false);
        RaftPeerSetCopy peerSetCopy = new RaftPeerSetCopy(memberManager, () -> {
            notifyReceived.set(true);
        });
        
        Collection<Member> firstEvent = Arrays.asList(
                Member.builder().ip("127.0.0.1").port(80).build(),
                Member.builder().ip("127.0.0.2").port(81).build(),
                Member.builder().ip("127.0.0.3").port(82).build());
        
        peerSetCopy.changePeers(firstEvent);
        Assert.assertTrue(notifyReceived.get());
        notifyReceived.set(false);
    
        Collection<Member> secondEvent = Arrays.asList(
                Member.builder().ip("127.0.0.1").port(80).build(),
                Member.builder().ip("127.0.0.2").port(81).build(),
                Member.builder().ip("127.0.0.3").port(82).build(),
                Member.builder().ip("127.0.0.4").port(83).build());
    
        peerSetCopy.changePeers(secondEvent);
        Assert.assertTrue(notifyReceived.get());
        notifyReceived.set(false);
    
        Collection<Member> thirdEvent = Arrays.asList(
                Member.builder().ip("127.0.0.1").port(80).build(),
                Member.builder().ip("127.0.0.2").port(81).build(),
                Member.builder().ip("127.0.0.5").port(82).build());
    
        peerSetCopy.changePeers(thirdEvent);
        Assert.assertTrue(notifyReceived.get());
        notifyReceived.set(false);
    
        Collection<Member> fourEvent = Arrays.asList(
                Member.builder().ip("127.0.0.1").port(80).build(),
                Member.builder().ip("127.0.0.2").port(81).build());
    
        peerSetCopy.changePeers(fourEvent);
        Assert.assertTrue(notifyReceived.get());
        notifyReceived.set(false);
    
        Collection<Member> fiveEvent = Arrays.asList(
                Member.builder().ip("127.0.0.1").port(80).build(),
                Member.builder().ip("127.0.0.3").port(81).build());
    
        peerSetCopy.changePeers(fiveEvent);
        Assert.assertTrue(notifyReceived.get());
        notifyReceived.set(false);
    }
    
    private static class RaftPeerSetCopy extends RaftPeerSet {
        
        private final Runnable runnable;
        
        public RaftPeerSetCopy(ServerMemberManager memberManager, Runnable callback) {
            super(memberManager);
            this.runnable = callback;
        }
        
        @Override
        protected void changePeers(Collection<Member> members) {
            this.runnable.run();
        }
    }
    
}
