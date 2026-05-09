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

package com.alibaba.nacos.core.distributed.raft.utils;

import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.distributed.raft.JRaftServer;
import com.alibaba.nacos.core.distributed.raft.RaftConfig;
import com.alibaba.nacos.core.distributed.raft.RaftSysConstants;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import com.alipay.sofa.jraft.CliService;
import com.alipay.sofa.jraft.RouteTable;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.rpc.RpcServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JRaftUtilsTest {
    
    private static final String GROUP = "naming_persistent_service";
    
    @Mock
    private ServerMemberManager serverMemberManager;
    
    @Mock
    private CliService cliService;
    
    @Mock
    private RouteTable routeTable;
    
    @Mock
    private JRaftServer server;
    
    private MockedStatic<ApplicationUtils> applicationUtilsMock;
    private MockedStatic<RouteTable> routeTableMock;
    
    @BeforeEach
    void setUp() {
        RaftConfig config = new RaftConfig();
        config.setVal(RaftSysConstants.RAFT_CORE_THREAD_NUM, "2");
        config.setVal(RaftSysConstants.RAFT_CLI_SERVICE_THREAD_NUM, "1");
        RaftExecutor.init(config);
    }
    
    @AfterEach
    void tearDown() {
        if (applicationUtilsMock != null) {
            try {
                applicationUtilsMock.close();
            } catch (Exception ignored) {
            }
            applicationUtilsMock = null;
        }
        if (routeTableMock != null) {
            try {
                routeTableMock.close();
            } catch (Exception ignored) {
            }
            routeTableMock = null;
        }
    }
    
    @Test
    void testToStringsWithEmptyList() {
        List<String> result = JRaftUtils.toStrings(Collections.emptyList());
        assertEquals(Collections.emptyList(), result);
    }
    
    @Test
    void testToStringsWithSinglePeerId() throws Exception {
        PeerId peerId = PeerId.parsePeer("127.0.0.1:8080");
        List<String> result = JRaftUtils.toStrings(Collections.singletonList(peerId));
        assertEquals(1, result.size());
        assertEquals("127.0.0.1:8080", result.get(0));
    }
    
    @Test
    void testToStringsWithMultiplePeerIds() throws Exception {
        PeerId p1 = PeerId.parsePeer("192.168.1.1:8848");
        PeerId p2 = PeerId.parsePeer("192.168.1.2:8848");
        List<String> result = JRaftUtils.toStrings(Arrays.asList(p1, p2));
        assertEquals(2, result.size());
        assertEquals("192.168.1.1:8848", result.get(0));
        assertEquals("192.168.1.2:8848", result.get(1));
    }
    
    @Test
    void testInitDirectory() throws Exception {
        java.io.File tempDir = Files.createTempDirectory("raft-utils-test").toFile();
        try {
            String parentPath = tempDir.getAbsolutePath();
            String groupName = "naming_persistent_service";
            com.alipay.sofa.jraft.option.NodeOptions copy =
                new com.alipay.sofa.jraft.option.NodeOptions();
            JRaftUtils.initDirectory(parentPath, groupName, copy);
            assertEquals(
                java.nio.file.Paths.get(parentPath, groupName, "log").toString(),
                copy.getLogUri());
            assertEquals(
                java.nio.file.Paths.get(parentPath, groupName, "snapshot").toString(),
                copy.getSnapshotUri());
            assertEquals(
                java.nio.file.Paths.get(parentPath, groupName, "meta-data").toString(),
                copy.getRaftMetaUri());
            assertTrue(new java.io.File(copy.getLogUri()).isDirectory());
            assertTrue(new java.io.File(copy.getSnapshotUri()).isDirectory());
            assertTrue(new java.io.File(copy.getRaftMetaUri()).isDirectory());
        } finally {
            try {
                com.alibaba.nacos.sys.utils.DiskUtils.deleteDirectory(tempDir.getAbsolutePath());
            } catch (Exception ignored) {
            }
        }
    }
    
    @Test
    void testToStringsWithNullListThrowsNpe() {
        assertThrows(NullPointerException.class, () -> JRaftUtils.toStrings(null));
    }
    
    @Test
    void testInitDirectoryWithEmptyGroupName() throws Exception {
        Path tempDir = Files.createTempDirectory("raft-utils-empty-group");
        try {
            String parentPath = tempDir.toFile().getAbsolutePath();
            com.alipay.sofa.jraft.option.NodeOptions copy =
                new com.alipay.sofa.jraft.option.NodeOptions();
            JRaftUtils.initDirectory(parentPath, "", copy);
            assertEquals(
                java.nio.file.Paths.get(parentPath, "", "log").toString(),
                copy.getLogUri());
            assertTrue(new File(copy.getLogUri()).isDirectory());
        } finally {
            try {
                com.alibaba.nacos.sys.utils.DiskUtils
                    .deleteDirectory(tempDir.toFile().getAbsolutePath());
            } catch (Exception ignored) {
            }
        }
    }
    
    @Test
    void testInitDirectoryThrowsWhenGroupPathIsFile() throws Exception {
        Path tempDir = Files.createTempDirectory("raft-utils-file-group");
        try {
            File groupAsFile = new File(tempDir.toFile(), "groupName");
            assertTrue(groupAsFile.createNewFile());
            String parentPath = tempDir.toFile().getAbsolutePath();
            com.alipay.sofa.jraft.option.NodeOptions copy =
                new com.alipay.sofa.jraft.option.NodeOptions();
            assertThrows(RuntimeException.class,
                () -> JRaftUtils.initDirectory(parentPath, "groupName", copy));
        } finally {
            try {
                com.alibaba.nacos.sys.utils.DiskUtils
                    .deleteDirectory(tempDir.toFile().getAbsolutePath());
            } catch (Exception ignored) {
            }
        }
    }
    
    // ---------- joinCluster ----------
    
    @Test
    void joinClusterWhenNotFirstIpReturnsImmediately() {
        applicationUtilsMock = Mockito.mockStatic(ApplicationUtils.class);
        applicationUtilsMock.when(() -> ApplicationUtils.getBean(ServerMemberManager.class))
            .thenReturn(serverMemberManager);
        when(serverMemberManager.isFirstIp()).thenReturn(false);
        
        Configuration conf = new Configuration();
        PeerId self = PeerId.parsePeer("127.0.0.1:8080");
        
        assertDoesNotThrow(() -> JRaftUtils.joinCluster(cliService,
            Collections.singletonList("127.0.0.1:8081"), conf, GROUP, self));
        
        verify(cliService, never()).addPeer(any(), any(), any());
    }
    
    @Test
    void joinClusterWhenFirstIpAndPeerAlreadyInConfExitsLoop() {
        applicationUtilsMock = Mockito.mockStatic(ApplicationUtils.class);
        routeTableMock = Mockito.mockStatic(RouteTable.class);
        applicationUtilsMock.when(() -> ApplicationUtils.getBean(ServerMemberManager.class))
            .thenReturn(serverMemberManager);
        when(serverMemberManager.isFirstIp()).thenReturn(true);
        
        PeerId peer = PeerId.parsePeer("127.0.0.1:8081");
        Configuration conf = new Configuration();
        conf.addPeer(peer);
        routeTableMock.when(RouteTable::getInstance).thenReturn(routeTable);
        when(routeTable.getConfiguration(GROUP)).thenReturn(conf);
        
        PeerId self = PeerId.parsePeer("127.0.0.1:8080");
        
        assertDoesNotThrow(() -> JRaftUtils.joinCluster(cliService,
            Collections.singletonList("127.0.0.1:8081"), conf, GROUP, self));
        
        verify(cliService, never()).addPeer(any(), any(), any());
    }
    
    @Test
    void joinClusterWhenFirstIpAndAddPeerSucceedsExitsLoop() {
        applicationUtilsMock = Mockito.mockStatic(ApplicationUtils.class);
        routeTableMock = Mockito.mockStatic(RouteTable.class);
        applicationUtilsMock.when(() -> ApplicationUtils.getBean(ServerMemberManager.class))
            .thenReturn(serverMemberManager);
        when(serverMemberManager.isFirstIp()).thenReturn(true);
        
        Configuration conf = new Configuration();
        conf.addPeer(PeerId.parsePeer("127.0.0.1:8080"));
        routeTableMock.when(RouteTable::getInstance).thenReturn(routeTable);
        when(routeTable.getConfiguration(GROUP)).thenReturn(conf);
        
        when(cliService.addPeer(eq(GROUP), any(Configuration.class), any(PeerId.class)))
            .thenReturn(Status.OK());
        
        PeerId self = PeerId.parsePeer("127.0.0.1:8080");
        
        assertDoesNotThrow(() -> JRaftUtils.joinCluster(cliService,
            Collections.singletonList("127.0.0.1:8081"), conf, GROUP, self));
        
        verify(cliService).addPeer(eq(GROUP), any(Configuration.class), any(PeerId.class));
    }
    
    // ---------- initRpcServer ----------
    
    @Test
    void initRpcServerReturnsNonNullAndReleasesInFinally() {
        RpcServer rpcServer = null;
        try {
            PeerId peerId = PeerId.parsePeer("127.0.0.1:18080");
            rpcServer = JRaftUtils.initRpcServer(server, peerId);
            assertNotNull(rpcServer);
        } finally {
            shutdownRpcServerQuietly(rpcServer);
        }
    }
    
    private static void shutdownRpcServerQuietly(RpcServer rpcServer) {
        try {
            if (rpcServer == null) {
                return;
            }
            rpcServer.shutdown();
        } catch (Exception ignored) {
        }
    }
}
