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

package com.alibaba.nacos.core.distributed.raft.auth;

import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberMetaDataConstants;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JRaftAuthUpgradeCoordinatorTest {
    
    @TempDir
    private Path temporaryDirectory;
    
    @Mock
    private ServerMemberManager serverMemberManager;
    
    @Test
    void testInitialStateAllowsInvalidCredential() {
        JRaftAuthUpgradeCoordinator coordinator = newCoordinator();
        
        assertFalse(coordinator.isEnforced());
        assertTrue(coordinator.allowInvalidCredential());
    }
    
    @Test
    void testDoesNotEnforceUntilEveryMemberSupportsAuthentication() {
        Member supported = memberWithCapability(true);
        Member unsupported = memberWithCapability(false);
        when(serverMemberManager.allMembers()).thenReturn(Arrays.asList(supported, unsupported));
        JRaftAuthUpgradeCoordinator coordinator = newCoordinator();
        
        coordinator.doCheck();
        
        assertFalse(coordinator.isEnforced());
        assertFalse(Files.exists(stateFile()));
    }
    
    @Test
    void testPersistsAfterEnforcingAndRemainsEnforced() {
        Member supported = memberWithCapability(true);
        List<Member> members = new ArrayList<>(Collections.singletonList(supported));
        when(serverMemberManager.allMembers()).thenReturn(members);
        JRaftAuthUpgradeCoordinator coordinator = newCoordinator();
        
        coordinator.doCheck();
        
        assertTrue(coordinator.isEnforced());
        assertTrue(Files.isRegularFile(stateFile()));
        assertFalse(coordinator.allowInvalidCredential());
        
        members.clear();
        members.add(memberWithCapability(false));
        coordinator.doCheck();
        assertTrue(coordinator.isEnforced());
    }
    
    @Test
    void testRestoresEnforcedStateFromMarker() throws Exception {
        Files.write(stateFile(), Arrays.asList("version=1", "state=ENFORCED"),
            StandardCharsets.UTF_8);
        
        JRaftAuthUpgradeCoordinator coordinator = newCoordinator();
        
        assertTrue(coordinator.isEnforced());
        assertFalse(coordinator.allowInvalidCredential());
    }
    
    @Test
    void testInvalidMarkerDoesNotEnableEnforcement() throws Exception {
        Files.write(stateFile(), Collections.singletonList("state=COMPATIBLE"),
            StandardCharsets.UTF_8);
        
        JRaftAuthUpgradeCoordinator coordinator = newCoordinator();
        
        assertFalse(coordinator.isEnforced());
    }
    
    @Test
    void testPersistenceFailureDoesNotBlockEnforcementAndRetries() throws Exception {
        Path parentFile = temporaryDirectory.resolve("not-a-directory");
        Files.write(parentFile, Collections.singletonList("content"), StandardCharsets.UTF_8);
        Path stateFile = parentFile.resolve(JRaftAuthUpgradeCoordinator.STATE_FILE_NAME);
        Member supported = memberWithCapability(true);
        when(serverMemberManager.allMembers()).thenReturn(Collections.singletonList(supported));
        JRaftAuthUpgradeCoordinator coordinator =
            new JRaftAuthUpgradeCoordinator(serverMemberManager, stateFile);
        
        coordinator.doCheck();
        
        assertTrue(coordinator.isEnforced());
        assertFalse(coordinator.allowInvalidCredential());
        assertFalse(Files.exists(stateFile));
        
        Files.delete(parentFile);
        Files.createDirectories(parentFile);
        coordinator.doCheck();
        
        assertTrue(Files.isRegularFile(stateFile));
    }
    
    private JRaftAuthUpgradeCoordinator newCoordinator() {
        return new JRaftAuthUpgradeCoordinator(serverMemberManager, stateFile());
    }
    
    private Path stateFile() {
        return temporaryDirectory.resolve(JRaftAuthUpgradeCoordinator.STATE_FILE_NAME);
    }
    
    private Member memberWithCapability(boolean supported) {
        Member member = mock(Member.class);
        lenient().when(member.getExtendVal(MemberMetaDataConstants.SUPPORT_JRAFT_AUTH))
            .thenReturn(supported);
        return member;
    }
}
