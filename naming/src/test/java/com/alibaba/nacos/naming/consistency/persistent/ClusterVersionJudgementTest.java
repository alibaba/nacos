/*
 *  Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.alibaba.nacos.naming.consistency.persistent;

import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberMetaDataConstants;
import com.alibaba.nacos.core.cluster.NodeState;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockServletContext;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ClusterVersionJudgementTest {
    
    private ServerMemberManager manager;
    
    private String[] ipList;
    
    private final int ipCount = 3;
    
    private final String ip1 = "1.1.1.1";
    
    private final String ip2 = "2.2.2.2";
    
    private final String ip3 = "3.3.3.3";
    
    private final int defalutPort = 80;
    
    private final String newVersion = "1.4.0";
    
    private final String oldVersion = "1.3.0";
    
    private List<Member> members;
    
    private Map<String, Object> newVersionMeta;
    
    private Map<String, Object> oldVersionMeta;
    
    private ClusterVersionJudgement judgement;
    
    public ClusterVersionJudgementTest() {
    }
    
    @BeforeClass
    public static void beforeClass() {
        EnvUtil.setEnvironment(new MockEnvironment());
    }
    
    @Before
    public void beforeMethod() throws Exception {
        manager = new ServerMemberManager(new MockServletContext());
        newVersionMeta = new HashMap<>(4);
        newVersionMeta.put(MemberMetaDataConstants.VERSION, newVersion);
        oldVersionMeta = new HashMap<>(4);
        oldVersionMeta.put(MemberMetaDataConstants.VERSION, oldVersion);
        ipList = new String[ipCount];
        ipList[0] = ip1;
        ipList[1] = ip2;
        ipList[2] = ip3;
        members = new LinkedList<>();
        members.add(Member.builder().ip(ipList[0]).port(defalutPort).state(NodeState.UP).build());
        members.add(Member.builder().ip(ipList[1]).port(defalutPort).state(NodeState.UP).build());
        members.add(Member.builder().ip(ipList[2]).port(defalutPort).state(NodeState.UP).build());
        manager.memberJoin(members);
    }
    
    @After
    public void afterMethod() throws Exception {
        manager.shutdown();
        manager = null;
    }
    
    /**
     * The member node has version information greater than 1.4.0
     */
    @Test
    public void testAllMemberIsNewVersion() {
        Collection<Member> allMembers = manager.allMembers();
        allMembers.forEach(member -> member.setExtendInfo(newVersionMeta));
        judgement = new ClusterVersionJudgement(manager);
        judgement.judge();
        Assert.assertTrue(judgement.allMemberIsNewVersion());
    }
    
    @Test
    public void testPartMemberIsNewVersion() {
        Collection<Member> allMembers = manager.allMembers();
        AtomicInteger count = new AtomicInteger();
        allMembers.forEach(member -> {
            if (count.get() == 0) {
                member.setExtendInfo(oldVersionMeta);
            } else {
                count.incrementAndGet();
                member.setExtendInfo(newVersionMeta);
            }
        });
        judgement = new ClusterVersionJudgement(manager);
        judgement.judge();
        Assert.assertFalse(judgement.allMemberIsNewVersion());
    }
    
    @Test
    public void testPartMemberUpdateToNewVersion() {
        // Firstly, make a cluster with a part of new version servers.
        Collection<Member> allMembers = manager.allMembers();
        AtomicInteger count = new AtomicInteger();
        allMembers.forEach(member -> {
            if (count.get() == 0) {
                member.setExtendInfo(oldVersionMeta);
            } else {
                count.incrementAndGet();
                member.setExtendInfo(newVersionMeta);
            }
        });
        judgement = new ClusterVersionJudgement(manager);
        judgement.judge();
        Assert.assertFalse(judgement.allMemberIsNewVersion());
        // Secondly, make all in the cluster to be new version servers.
        allMembers.forEach(member -> member.setExtendInfo(newVersionMeta));
        judgement = new ClusterVersionJudgement(manager);
        judgement.judge();
        Assert.assertTrue(judgement.allMemberIsNewVersion());
    }
    
}
