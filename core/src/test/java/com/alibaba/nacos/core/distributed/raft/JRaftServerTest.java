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

package com.alibaba.nacos.core.distributed.raft;

import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class JRaftServerTest {
    
    private JRaftServer server;
    
    @BeforeClass
    public static void beforeClass() {
        EnvUtil.setEnvironment(new MockEnvironment());
    }
    
    @Before
    public void before() {
        RaftConfig config = new RaftConfig();
        Collection<Member> initEvent = Collections.singletonList(Member.builder().ip("1.1.1.1").port(7848).build());
        config.setMembers("1.1.1.1:7848", ProtocolManager.toCPMembersInfo(initEvent));
        
        server = new JRaftServer() {
            
            @Override
            boolean peerChange(JRaftMaintainService maintainService, Set<String> newPeers) {
                return super.peerChange(maintainService, newPeers);
            }
        };
        
        server.init(config);
        
        Map<String, JRaftServer.RaftGroupTuple> map = new HashMap<>();
        map.put("test_nacos", new JRaftServer.RaftGroupTuple());
        server.mockMultiRaftGroup(map);
    }
    
    @Test
    public void testPeerChange() {
        AtomicBoolean changed = new AtomicBoolean(false);
        
        JRaftMaintainService service = new JRaftMaintainService(server) {
            @Override
            public RestResult<String> execute(Map<String, String> args) {
                changed.set(true);
                return RestResultUtils.success();
            }
        };
        
        Collection<Member> firstEvent = Arrays.asList(Member.builder().ip("1.1.1.1").port(7848).build(),
                Member.builder().ip("127.0.0.1").port(80).build(), Member.builder().ip("127.0.0.2").port(81).build(),
                Member.builder().ip("127.0.0.3").port(82).build());
        server.peerChange(service, ProtocolManager.toCPMembersInfo(firstEvent));
        Assert.assertFalse(changed.get());
        changed.set(false);
        
        Collection<Member> secondEvent = Arrays.asList(Member.builder().ip("1.1.1.1").port(7848).build(),
                Member.builder().ip("127.0.0.1").port(80).build(), Member.builder().ip("127.0.0.2").port(81).build(),
                Member.builder().ip("127.0.0.4").port(83).build());
        server.peerChange(service, ProtocolManager.toCPMembersInfo(secondEvent));
        Assert.assertTrue(changed.get());
        changed.set(false);
        
        Collection<Member> thirdEvent = Arrays.asList(Member.builder().ip("1.1.1.1").port(7848).build(),
                Member.builder().ip("127.0.0.2").port(81).build(),
                Member.builder().ip("127.0.0.5").port(82).build());
        server.peerChange(service, ProtocolManager.toCPMembersInfo(thirdEvent));
        Assert.assertTrue(changed.get());
        changed.set(false);
        
        // remove Member.builder().ip("127.0.0.2").port(81).build()
        Collection<Member> fourEvent = Arrays.asList(Member.builder().ip("1.1.1.1").port(7848).build(),
                Member.builder().ip("127.0.0.1").port(80).build());
        server.peerChange(service, ProtocolManager.toCPMembersInfo(fourEvent));
        Assert.assertTrue(changed.get());
        changed.set(false);
        
        Collection<Member> fiveEvent = Arrays.asList(Member.builder().ip("1.1.1.1").port(7848).build(),
                Member.builder().ip("127.0.0.1").port(80).build(), Member.builder().ip("127.0.0.3").port(81).build());
        server.peerChange(service, ProtocolManager.toCPMembersInfo(fiveEvent));
        Assert.assertFalse(changed.get());
        changed.set(false);
    }
}
