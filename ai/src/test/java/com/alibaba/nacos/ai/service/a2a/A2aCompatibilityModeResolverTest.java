/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.service.a2a;

import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberMetaDataConstants;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class A2aCompatibilityModeResolverTest {
    
    @Test
    void shouldDefaultToCanonical() {
        ServerMemberManager memberManager = mock(ServerMemberManager.class);
        assertEquals(A2aCompatibilityMode.CANONICAL,
            new A2aCompatibilityModeResolver(memberManager, () -> null).resolve());
        assertEquals(A2aCompatibilityMode.CANONICAL,
            new A2aCompatibilityModeResolver(memberManager, () -> "  ").resolve());
        try (MockedStatic<EnvUtil> envUtil = mockStatic(EnvUtil.class)) {
            envUtil.when(() -> EnvUtil.getProperty(A2aCompatibilityModeResolver.MODE_PROPERTY,
                A2aCompatibilityMode.CANONICAL.name())).thenReturn("CANONICAL");
            assertEquals(A2aCompatibilityMode.CANONICAL,
                new A2aCompatibilityModeResolver(memberManager).resolve());
        }
    }
    
    @Test
    void shouldParseExplicitModesCaseInsensitively() {
        ServerMemberManager memberManager = mock(ServerMemberManager.class);
        assertEquals(A2aCompatibilityMode.CANONICAL,
            new A2aCompatibilityModeResolver(memberManager, () -> " canonical ").resolve());
        assertEquals(A2aCompatibilityMode.LEGACY,
            new A2aCompatibilityModeResolver(memberManager, () -> "legacy").resolve());
        assertThrows(IllegalArgumentException.class,
            () -> new A2aCompatibilityModeResolver(memberManager, () -> "unknown").resolve());
    }
    
    @Test
    void shouldKeepAutoLegacyWithoutCompleteSupportedMembership() {
        ServerMemberManager memberManager = mock(ServerMemberManager.class);
        A2aCompatibilityModeResolver resolver =
            new A2aCompatibilityModeResolver(memberManager, () -> "AUTO");
        Member missing = memberWithVersion(null);
        Member nonString = memberWithValue(330);
        Member blank = memberWithVersion("");
        Member invalid = memberWithVersion("invalid");
        Member old = memberWithVersion("3.2.9");
        when(memberManager.allMembers()).thenReturn(null, Collections.emptyList(),
            Collections.singletonList(missing), Collections.singletonList(nonString),
            Collections.singletonList(blank), Collections.singletonList(invalid),
            Collections.singletonList(old));
        for (int i = 0; i < 7; i++) {
            assertEquals(A2aCompatibilityMode.LEGACY, resolver.resolve());
        }
    }
    
    @Test
    void shouldSwitchAutoToCanonicalOnlyOnce() {
        ServerMemberManager memberManager = mock(ServerMemberManager.class);
        Member current = memberWithVersion("3.3.0-SNAPSHOT");
        Member future = memberWithVersion("3.4.1");
        Member old = memberWithVersion("3.2.0");
        when(memberManager.allMembers()).thenReturn(Arrays.asList(current, future),
            Collections.singletonList(old));
        A2aCompatibilityModeResolver resolver =
            new A2aCompatibilityModeResolver(memberManager, () -> "auto");
        assertEquals(A2aCompatibilityMode.CANONICAL, resolver.resolve());
        assertEquals(A2aCompatibilityMode.CANONICAL, resolver.resolve());
    }
    
    private Member memberWithVersion(String version) {
        return memberWithValue(version);
    }
    
    private Member memberWithValue(Object version) {
        Member member = mock(Member.class);
        when(member.getExtendVal(MemberMetaDataConstants.VERSION)).thenReturn(version);
        return member;
    }
}
