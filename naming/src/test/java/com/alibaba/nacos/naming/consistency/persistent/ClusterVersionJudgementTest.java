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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ClusterVersionJudgementTest {
    
    private ServerMemberManager manager;
    
    @BeforeClass
    public static void beforeClass() {
        EnvUtil.setEnvironment(new MockEnvironment());
    }
    
    @Before
    public void beforeMethod() throws Exception {
        manager = new ServerMemberManager(new MockServletContext());
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
        Map<String, String> metadataInfo = new HashMap<>(4);
        metadataInfo.put(MemberMetaDataConstants.VERSION, "1.4.0");
        Collection<Member> members = Arrays
                .asList(Member.builder().ip("1.1.1.1").port(80).state(NodeState.UP).extendInfo(metadataInfo).build(),
                        Member.builder().ip("2.2.2.2").port(80).state(NodeState.UP).extendInfo(metadataInfo).build(),
                        Member.builder().ip("3.3.3.3").port(80).state(NodeState.UP).extendInfo(metadataInfo).build());
        manager.memberJoin(members);
        ClusterVersionJudgement judgement = new ClusterVersionJudgement(manager);
        judgement.judge();
        Assert.assertTrue(judgement.allMemberIsNewVersion());
    }
    
    @Test
    public void testPartMemberIsNewVersion() {
        Map<String, String> metadataInfo = new HashMap<>(4);
        metadataInfo.put(MemberMetaDataConstants.VERSION, "1.4.0");
        Collection<Member> members = Arrays
                .asList(Member.builder().ip("1.1.1.1").port(80).state(NodeState.UP).extendInfo(metadataInfo).build(),
                        Member.builder().ip("2.2.2.2").port(80).state(NodeState.UP).extendInfo(metadataInfo).build(),
                        Member.builder().ip("3.3.3.3").port(80).state(NodeState.UP).build());
        manager.memberJoin(members);
        ClusterVersionJudgement judgement = new ClusterVersionJudgement(manager);
        judgement.judge();
        Assert.assertFalse(judgement.allMemberIsNewVersion());
    }
    
    @Test
    public void testPartMemberUpdateToNewVersion() {
        Map<String, String> metadataInfo = new HashMap<>(4);
        metadataInfo.put(MemberMetaDataConstants.VERSION, "1.4.0");
        Collection<Member> members = Arrays
                .asList(Member.builder().ip("1.1.1.1").port(80).state(NodeState.UP).extendInfo(metadataInfo).build(),
                        Member.builder().ip("2.2.2.2").port(80).state(NodeState.UP).extendInfo(metadataInfo).build(),
                        Member.builder().ip("3.3.3.3").port(80).state(NodeState.UP).build());
        manager.memberJoin(members);
        ClusterVersionJudgement judgement = new ClusterVersionJudgement(manager);
        judgement.judge();
        Assert.assertFalse(judgement.allMemberIsNewVersion());
        
        // update 3.3.3.3:80 version to 1.4.0
        manager.update(Member.builder().ip("3.3.3.3").port(80).state(NodeState.UP).extendInfo(metadataInfo).build());
        
        judgement.registerObserver(Assert::assertTrue, 0);
        judgement.judge();
        Assert.assertTrue(judgement.allMemberIsNewVersion());
    }
    
}
