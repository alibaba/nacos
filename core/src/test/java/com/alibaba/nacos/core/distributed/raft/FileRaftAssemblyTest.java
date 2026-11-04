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

package com.alibaba.nacos.core.distributed.raft;

import com.alibaba.nacos.consistency.cp.RequestProcessor4CP;
import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.core.distributed.raft.auth.JRaftAuthUpgradeCoordinator;
import com.alibaba.nacos.core.distributed.raft.storage.FileJRaftServiceFactory;
import com.alibaba.nacos.core.distributed.raft.storage.RaftProcessSupport;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alipay.sofa.jraft.Node;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises property binding and production group construction in isolated real JVMs.
 * Failed tests retain the temporary directory containing node files and phase logs.
 */
public class FileRaftAssemblyTest {
    
    private static final long PHASE_TIMEOUT_SECONDS = 90;
    
    private static final long PROCESS_OVERHEAD_SECONDS = 30;
    
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    Path directory;
    
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bindsFileBackendAndRestoresTwoProductionGroupsWithoutRocksDb(boolean delayedReplay)
        throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        for (String phase : List.of("write", "recover")) {
            Path log = directory.resolve(phase + ".log");
            Process child = RaftProcessSupport.start(FileRaftAssemblyTest.class, log,
                directory.toString(), Integer.toString(port), phase,
                Boolean.toString(delayedReplay));
            try {
                assertTrue(child.waitFor(PHASE_TIMEOUT_SECONDS + PROCESS_OVERHEAD_SECONDS,
                    TimeUnit.SECONDS),
                    () -> "Assembly JVM timed out: " + log + "\n"
                        + readLog(log));
                assertEquals(0, child.exitValue(), Files.readString(log));
                assertTrue(Files.readString(log).contains("ASSEMBLY_OK"), log.toString());
                RaftProcessSupport.assertNoRocksDb(log);
            } finally {
                child.destroyForcibly();
                assertTrue(child.waitFor(10, TimeUnit.SECONDS));
            }
        }
    }
    
    /**
     * Starts production groups through the same binding and service-factory selection as Nacos.
     * @param args home, Raft port, phase and whether recovery replay is delayed
     * @throws Exception on binding, Raft or recovery failures
     */
    public static void main(String[] args) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(PHASE_TIMEOUT_SECONDS);
        Path home = Path.of(args[0]);
        EnvUtil.setNacosHomePath(home.toString());
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("file-raft-test", Map.of(
            "nacos.core.protocol.raft.data.log_storage_type", "file",
            "nacos.core.protocol.raft.data.sync", "true",
            "nacos.plugin.auth.type", "nacos",
            "nacos.core.auth.server.identity.key", "test-key",
            "nacos.core.auth.server.identity.value", "test-value")));
        EnvUtil.setEnvironment(environment);
        RaftConfig config = Binder.get(environment)
            .bind("nacos.core.protocol.raft", Bindable.of(RaftConfig.class)).get();
        assertEquals("file", config.getVal("log_storage_type"));
        String self = "127.0.0.1:" + args[1];
        config.setMembers(self, Set.of(self));
        // Only the unrelated rolling-upgrade coordinator is stubbed; RPC and storage are real.
        JRaftAuthUpgradeCoordinator coordinator = mock(JRaftAuthUpgradeCoordinator.class);
        when(coordinator.allowInvalidCredential()).thenReturn(true);
        JRaftServer server = new JRaftServer(coordinator);
        server.init(config);
        boolean recovering = "recover".equals(args[2]);
        boolean delayedReplay = recovering && Boolean.parseBoolean(args[3]);
        List<Processor> processors = List.of(new Processor("assembly-a", delayedReplay, deadline),
            new Processor("assembly-b", delayedReplay, deadline));
        try {
            server.createMultiRaftGroup(new ArrayList<>(processors));
            server.start();
            for (Processor processor : processors) {
                Node node = server.findNodeByGroup(processor.group());
                assertInstanceOf(FileJRaftServiceFactory.class,
                    node.getOptions().getServiceFactory());
                while (!node.isLeader() && System.nanoTime() < deadline) {
                    Thread.sleep(50);
                }
                assertTrue(node.isLeader(), processor.group());
            }
            if (delayedReplay) {
                // Both FSMs must reach the pause before either is allowed to finish replay.
                for (Processor processor : processors) {
                    assertTrue(processor.replayStarted.await(remainingNanos(deadline),
                        TimeUnit.NANOSECONDS),
                        "Recovery replay did not start: " + processor.group());
                    assertTrue(processor.values.isEmpty(),
                        "Paused replay must not update state: " + processor.group());
                }
                processors.forEach(processor -> processor.releaseReplay.countDown());
            }
            for (Processor processor : processors) {
                Node node = server.findNodeByGroup(processor.group());
                if (recovering) {
                    // Leadership can precede replay of the previously committed entries.
                    assertTrue(processor.firstApplied.await(remainingNanos(deadline),
                        TimeUnit.NANOSECONDS),
                        () -> "Recovery did not apply the persisted entry: group="
                            + processor.group()
                            + ", lastLog=" + node.getLastLogIndex()
                            + ", committed=" + node.getLastCommittedIndex()
                            + ", applied=" + node.getLastAppliedLogIndex()
                            + ", values=" + processor.values);
                    assertEquals(List.of("first"), processor.values);
                }
                String value = recovering ? "second" : "first";
                WriteRequest request = WriteRequest.newBuilder().setGroup(processor.group())
                    .setOperation("ADD").setData(ByteString.copyFromUtf8(value)).build();
                Response response = server.commit(processor.group(), request,
                    new CompletableFuture<>()).get(remainingNanos(deadline), TimeUnit.NANOSECONDS);
                assertTrue(response.getSuccess());
                assertEquals(recovering ? List.of("first", "second")
                    : List.of("first"), processor.values);
                assertTrue(Files.isRegularFile(home.resolve("data/protocol/raft")
                    .resolve(processor.group()).resolve("log/file-log/manifest")));
            }
        } finally {
            for (Processor processor : processors) {
                processor.releaseReplay.countDown();
            }
            List<Node> nodes =
                processors.stream().map(p -> server.findNodeByGroup(p.group())).toList();
            server.shutdown();
            for (Node node : nodes) {
                if (node != null) {
                    node.join();
                }
            }
        }
        System.out.println("ASSEMBLY_OK");
        System.exit(0);
    }
    
    private static long remainingNanos(long deadline) {
        long remaining = deadline - System.nanoTime();
        assertTrue(remaining > 0, "Assembly phase deadline expired");
        return remaining;
    }
    
    private static String readLog(Path log) {
        try {
            return Files.readString(log);
        } catch (java.io.IOException e) {
            return "Unable to read phase log: " + e;
        }
    }
    
    private static final class Processor extends RequestProcessor4CP {
        
        private final String group;
        
        private final List<String> values = new java.util.concurrent.CopyOnWriteArrayList<>();
        
        private final boolean delayedReplay;
        
        private final long deadline;
        
        private final CountDownLatch replayStarted = new CountDownLatch(1);
        
        private final CountDownLatch releaseReplay = new CountDownLatch(1);
        
        private final CountDownLatch firstApplied = new CountDownLatch(1);
        
        private Processor(String group, boolean delayedReplay, long deadline) {
            this.group = group;
            this.delayedReplay = delayedReplay;
            this.deadline = deadline;
        }
        
        @Override
        public Response onRequest(ReadRequest request) {
            return Response.newBuilder().setSuccess(true).build();
        }
        
        @Override
        public Response onApply(WriteRequest request) {
            if (delayedReplay && firstApplied.getCount() > 0) {
                replayStarted.countDown();
                try {
                    if (!releaseReplay.await(remainingNanos(deadline), TimeUnit.NANOSECONDS)) {
                        throw new IllegalStateException(
                            "Recovery replay was not released: " + group);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Recovery replay interrupted: " + group, e);
                }
            }
            values.add(request.getData().toStringUtf8());
            firstApplied.countDown();
            return Response.newBuilder().setSuccess(true).build();
        }
        
        @Override
        public String group() {
            return group;
        }
    }
}
