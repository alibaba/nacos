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

package com.alibaba.nacos.core.cluster;

import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.ConfigurableEnvironment;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class MemberUtilsTest {
    
    private static final String IP = "1.1.1.1";
    
    private static final int PORT = 8848;
    
    @Mock
    private ConfigurableEnvironment environment;
    
    private Member originalMember;
    
    @Before
    public void setUp() {
        ApplicationUtils.injectEnvironment(environment);
        originalMember = buildMember();
    }
    
    private Member buildMember() {
        return Member.builder().ip(IP).port(PORT).state(NodeState.UP).build();
    }
    
    @Test
    public void testIsBasicInfoChangedNoChangeWithoutExtendInfo() {
        Member newMember = buildMember();
        assertFalse(MemberUtils.isBasicInfoChanged(newMember, originalMember));
    }
    
    @Test
    public void testIsBasicInfoChangedNoChangeWithExtendInfo() {
        Member newMember = buildMember();
        newMember.setExtendVal("test", "test");
        assertFalse(MemberUtils.isBasicInfoChanged(newMember, originalMember));
    }
    
    @Test
    public void testIsBasicInfoChangedForIp() {
        Member newMember = buildMember();
        newMember.setIp("1.1.1.2");
        assertTrue(MemberUtils.isBasicInfoChanged(newMember, originalMember));
    }
    
    @Test
    public void testIsBasicInfoChangedForPort() {
        Member newMember = buildMember();
        newMember.setPort(PORT + 1);
        assertTrue(MemberUtils.isBasicInfoChanged(newMember, originalMember));
    }
    
    @Test
    public void testIsBasicInfoChangedForAddress() {
        Member newMember = buildMember();
        newMember.setAddress("test");
        assertTrue(MemberUtils.isBasicInfoChanged(newMember, originalMember));
    }
    
    @Test
    public void testIsBasicInfoChangedForStatus() {
        Member newMember = buildMember();
        newMember.setState(NodeState.DOWN);
        assertTrue(MemberUtils.isBasicInfoChanged(newMember, originalMember));
    }
    
    @Test
    public void testIsBasicInfoChangedForMoreBasicExtendInfo() {
        Member newMember = buildMember();
        newMember.setExtendVal(MemberMetaDataConstants.VERSION, "TEST");
        assertTrue(MemberUtils.isBasicInfoChanged(newMember, originalMember));
    }
    
    @Test
    public void testIsBasicInfoChangedForChangedBasicExtendInfo() {
        Member newMember = buildMember();
        newMember.setExtendVal(MemberMetaDataConstants.WEIGHT, "100");
        assertTrue(MemberUtils.isBasicInfoChanged(newMember, originalMember));
    }
}
