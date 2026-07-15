/*
 *  Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.auth;

import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberMetaDataConstants;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * {@link InnerApiAuthEnabled} unit test.
 *
 * @author xiweng.yy
 */
@ExtendWith(MockitoExtension.class)
class InnerApiAuthEnabledTest {
    
    @Mock
    private ServerMemberManager serverMemberManager;
    
    private InnerApiAuthEnabled innerApiAuthEnabled;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new MockEnvironment());
        innerApiAuthEnabled = new InnerApiAuthEnabled(serverMemberManager);
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(null);
    }
    
    @Test
    void testIsEnabledInitiallyFalse() {
        assertFalse(innerApiAuthEnabled.isEnabled());
    }
    
    @Test
    void testDoCheckStaysDisabledWhenMemberVersionBlank() {
        Member member = Member.builder().build();
        member.setExtendVal(MemberMetaDataConstants.VERSION, "");
        when(serverMemberManager.allMembers()).thenReturn(Collections.singletonList(member));
        
        innerApiAuthEnabled.doCheck();
        
        assertFalse(innerApiAuthEnabled.isEnabled());
    }
    
    @Test
    void testDoCheckStaysDisabledWhenMemberVersionNot3x() {
        Member member = Member.builder().build();
        member.setExtendVal(MemberMetaDataConstants.VERSION, "2.2.3");
        when(serverMemberManager.allMembers()).thenReturn(Collections.singletonList(member));
        
        innerApiAuthEnabled.doCheck();
        
        assertFalse(innerApiAuthEnabled.isEnabled());
    }
    
    @Test
    void testDoCheckStaysDisabledWhenOneMemberNot3x() {
        Member m1 = Member.builder().build();
        m1.setExtendVal(MemberMetaDataConstants.VERSION, "3.0.0");
        Member m2 = Member.builder().build();
        m2.setExtendVal(MemberMetaDataConstants.VERSION, "2.2.3");
        when(serverMemberManager.allMembers()).thenReturn(Arrays.asList(m1, m2));
        
        innerApiAuthEnabled.doCheck();
        
        assertFalse(innerApiAuthEnabled.isEnabled());
    }
    
    @Test
    void testDoCheckEnablesWhenAllMembers3x() {
        Member m1 = Member.builder().build();
        m1.setExtendVal(MemberMetaDataConstants.VERSION, "3.0.0");
        Member m2 = Member.builder().build();
        m2.setExtendVal(MemberMetaDataConstants.VERSION, "3.1.0");
        when(serverMemberManager.allMembers()).thenReturn(Arrays.asList(m1, m2));
        
        innerApiAuthEnabled.doCheck();
        
        assertTrue(innerApiAuthEnabled.isEnabled());
    }
    
    @Test
    void testDoCheckEnablesWhenAllMembersHave3xVersion() {
        Member member = Member.builder().build();
        member.setExtendVal(MemberMetaDataConstants.VERSION, "3.2.0");
        when(serverMemberManager.allMembers()).thenReturn(Collections.singletonList(member));
        
        innerApiAuthEnabled.doCheck();
        
        assertTrue(innerApiAuthEnabled.isEnabled());
    }
    
    @Test
    void testDoCheckReturnsEarlyWhenAlreadyEnabled() {
        Member m1 = Member.builder().build();
        m1.setExtendVal(MemberMetaDataConstants.VERSION, "3.0.0");
        when(serverMemberManager.allMembers()).thenReturn(Collections.singletonList(m1));
        
        innerApiAuthEnabled.doCheck();
        assertTrue(innerApiAuthEnabled.isEnabled());
        
        innerApiAuthEnabled.doCheck();
        
        assertTrue(innerApiAuthEnabled.isEnabled());
    }
}
