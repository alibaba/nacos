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

package com.alibaba.nacos.core.distributed.raft.storage;

import com.alibaba.nacos.core.distributed.raft.grpc.NacosGrpcRaftRpcFactory;
import com.alibaba.nacos.core.distributed.raft.storage.file.FileLogStorage;
import com.alipay.sofa.jraft.core.DefaultJRaftServiceFactory;
import com.alipay.sofa.jraft.entity.LogEntry;
import com.alipay.sofa.jraft.entity.EnumOutter.EntryType;
import com.alipay.sofa.jraft.option.RaftOptions;
import com.alipay.sofa.jraft.storage.LogStorage;
import com.alipay.sofa.jraft.rpc.RpcServer;
import com.alipay.sofa.jraft.rpc.RpcProcessor;
import com.alipay.sofa.jraft.rpc.RpcContext;
import com.alipay.sofa.jraft.rpc.RpcRequests;
import com.alipay.sofa.jraft.rpc.RaftRpcServerFactory;
import com.alipay.sofa.jraft.rpc.impl.ConnectionClosedEventListener;
import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Iterator;
import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.RaftGroupService;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.conf.ConfigurationEntry;
import com.alipay.sofa.jraft.core.NodeImpl;
import com.alipay.sofa.jraft.storage.LogManager;
import com.alipay.sofa.jraft.entity.RaftOutter.SnapshotMeta;
import java.lang.reflect.Field;
import com.alipay.sofa.jraft.core.StateMachineAdapter;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.entity.Task;
import com.alipay.sofa.jraft.error.RaftError;
import com.alipay.sofa.jraft.option.NodeOptions;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotReader;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotWriter;
import com.alipay.sofa.jraft.util.RpcFactoryHelper;
import com.alipay.sofa.jraft.util.Describer;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.CleanupMode;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

/**
 * Real RPC and disk recovery exercised in a JVM without the RocksDB JNI artifact.
 * Failed tests retain the temporary directory containing node files and child logs.
 */
public class FileRaftClusterTest {
    
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    Path directory;
    
    @ParameterizedTest
    @ValueSource(strings = {"base", "restart", "live", "configuration"})
    void replicatesRecoversAndInstallsSnapshotWithoutRocksDb(String scenario) throws Exception {
        Path output = directory.resolve("child.log");
        Process process = RaftProcessSupport.start(FileRaftClusterTest.class, output,
            directory.toString(), scenario);
        try {
            assertTrue(process.waitFor(240, TimeUnit.SECONDS), "Cluster JVM timed out: " + output);
            String log = Files.readString(output);
            assertEquals(0, process.exitValue(), log);
            assertTrue(log.contains("FILE_RAFT_CLUSTER_OK"), log);
            assertFalse(log.contains("com.alipay.sofa.jraft.storage.impl.RocksDBLogStorage"), log);
            assertFalse(log.contains("org.rocksdb."), log);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                assertTrue(process.waitFor(10, TimeUnit.SECONDS), output.toString());
            }
        }
    }
    
    /**
     * Runs the isolated three-node scenario.
     * @param args temporary root directory and scenario
     * @throws Exception on startup, replication or recovery failure
     */
    public static void main(String[] args) throws Exception {
        assertInstanceOf(NacosGrpcRaftRpcFactory.class, RpcFactoryHelper.rpcFactory());
        if ("base".equals(args[1])) {
            try (Cluster cluster = new Cluster(Path.of(args[0]))) {
                cluster.startAll();
                cluster.write(1);
                cluster.awaitValue(1);
                int formerLeader = cluster.leader();
                cluster.stop(formerLeader);
                cluster.write(2);
                cluster.start(formerLeader);
                cluster.awaitValue(2);
                
                int lagging = (cluster.leader() + 1) % 3;
                cluster.stop(lagging);
                for (int value = 3; value <= 20; value++) {
                    cluster.write(value);
                }
                // Two snapshots discard both the old log prefix and its previous-snapshot reserve.
                cluster.snapshot();
                cluster.write(21);
                cluster.snapshot();
                cluster.start(lagging);
                cluster.awaitValue(21);
                await(() -> cluster.machines[lagging].snapshotLoads > 0,
                    "lagging follower did not install a snapshot");
                cluster.write(22);
                cluster.awaitValue(22);
                
                for (int index = 0; index < 3; index++) {
                    cluster.stop(index);
                }
                cluster.startAll();
                cluster.awaitValue(22);
                cluster.write(23);
                cluster.awaitValue(23);
                for (int index = 0; index < 3; index++) {
                    assertTrue(Files.isDirectory(cluster.root.resolve("node-" + index)
                        .resolve("log/file-log")));
                }
            }
            try (Cluster cluster = new Cluster(Path.of(args[0]).resolve("conflict"))) {
                cluster.startAll();
                cluster.conflictingLeaderRejoins();
                cluster.membershipSurvivesSnapshots();
            }
        } else {
            try (Cluster cluster = new Cluster(Path.of(args[0]).resolve(args[1]))) {
                cluster.startAll();
                cluster.conflictingSnapshotResetsAndRecovers("restart".equals(args[1]),
                    "configuration".equals(args[1]));
            }
        }
        System.out.println("FILE_RAFT_CLUSTER_OK");
    }
    
    private static void await(BooleanSupplier condition, String message) throws Exception {
        await(condition, () -> message, TimeUnit.SECONDS.toNanos(20));
    }
    
    private static void await(BooleanSupplier condition, Supplier<String> message,
        long timeoutNanos) throws Exception {
        long deadline = System.nanoTime() + timeoutNanos;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(condition.getAsBoolean(), message);
    }
    
    static void awaitLeadership(BooleanSupplier condition, PeerId target, Status lastStatus,
        Supplier<String> diagnostics, long timeoutNanos) throws Exception {
        await(condition, () -> "Target did not become leader: target=" + target
            + ", lastStatus=" + lastStatus + "\n" + diagnostics.get(), timeoutNanos);
    }
    
    private static final class Cluster implements AutoCloseable {
        
        private final Path root;
        
        private final List<PeerId> peers = new ArrayList<>();
        
        private final RaftGroupService[] services = new RaftGroupService[4];
        
        private final Machine[] machines = new Machine[4];
        
        private final FileLogStorage[] stores = new FileLogStorage[4];
        
        private final AtomicIntegerArray resetCalls = new AtomicIntegerArray(4);
        
        private final AtomicLongArray resetIndexes = new AtomicLongArray(4);
        
        private final SnapshotMeta[] installMetadata = new SnapshotMeta[4];
        
        private volatile int pausedAppends = -1;
        
        private volatile int isolated = -1;
        
        private List<PeerId> configuration;
        
        private Cluster(Path root) throws IOException {
            this.root = root;
            List<ServerSocket> reservations = new ArrayList<>();
            try {
                for (int index = 0; index < 4; index++) {
                    ServerSocket socket = new ServerSocket(0);
                    reservations.add(socket);
                    peers.add(new PeerId("127.0.0.1", socket.getLocalPort()));
                }
                configuration = new ArrayList<>(peers.subList(0, 3));
            } finally {
                for (ServerSocket socket : reservations) {
                    socket.close();
                }
            }
        }
        
        private void startAll() throws IOException {
            for (int index = 0; index < 3; index++) {
                start(index);
            }
        }
        
        private void start(int index) throws IOException {
            Path home = Files.createDirectories(root.resolve("node-" + index));
            NodeOptions options = new NodeOptions();
            options.setServiceFactory(new DefaultJRaftServiceFactory() {
                
                @Override
                public LogStorage createLogStorage(String uri, RaftOptions raftOptions) {
                    stores[index] = spy(new FileLogStorage(Path.of(uri), raftOptions));
                    doAnswer(invocation -> {
                        boolean reset = (boolean) invocation.callRealMethod();
                        if (reset) {
                            resetIndexes.set(index, invocation.getArgument(0));
                            resetCalls.incrementAndGet(index);
                        }
                        return reset;
                    }).when(stores[index]).reset(anyLong());
                    return stores[index];
                }
            });
            options.setElectionTimeoutMs(800);
            options.setInitialConf(new Configuration(index == 3
                ? java.util.Collections.emptyList() : peers.subList(0, 3)));
            options.setLogUri(home.resolve("log").toString());
            options.setRaftMetaUri(home.resolve("meta").toString());
            options.setSnapshotUri(home.resolve("snapshot").toString());
            options.setSnapshotIntervalSecs(0);
            options.setSnapshotLogIndexMargin(0);
            options.getRaftOptions().setSync(true);
            options.getRaftOptions().setDisruptorBufferSize(1024);
            options.setRaftRpcThreadPoolSize(2);
            options.setCliRpcThreadPoolSize(1);
            machines[index] = new Machine();
            options.setFsm(machines[index]);
            RpcServer server = new PartitionServer(
                RpcFactoryHelper.rpcFactory().createRpcServer(peers.get(index).getEndpoint()),
                index);
            RaftRpcServerFactory.addRaftRequestProcessors(server, null, null);
            services[index] =
                new RaftGroupService("file-storage-cluster", peers.get(index), options, server,
                    false);
            services[index].start();
        }
        
        private int leader() throws Exception {
            await(() -> findLeader() >= 0, "No elected leader");
            return findLeader();
        }
        
        private int findLeader() {
            for (int index = 0; index < services.length; index++) {
                if (index != isolated && services[index] != null
                    && services[index].getRaftNode().isLeader()) {
                    return index;
                }
            }
            return -1;
        }
        
        private void write(int value) throws Exception {
            Node node = services[leader()].getRaftNode();
            CompletableFuture<Status> result = new CompletableFuture<>();
            node.apply(new Task(ByteBuffer.allocate(4).putInt(value).flip(), result::complete));
            Status status = result.get(20, TimeUnit.SECONDS);
            assertTrue(status.isOk(), status.toString());
        }
        
        private void snapshot() throws Exception {
            CompletableFuture<Status> result = new CompletableFuture<>();
            services[leader()].getRaftNode().snapshot(result::complete);
            Status status = result.get(20, TimeUnit.SECONDS);
            assertTrue(status.isOk(), status.toString());
        }
        
        private void awaitValue(int expected) throws Exception {
            await(() -> java.util.stream.IntStream.range(0, services.length)
                .filter(index -> services[index] != null && index != isolated)
                .allMatch(index -> machines[index].value == expected),
                "State machines did not converge to " + expected);
        }
        
        private void conflictingLeaderRejoins() throws Exception {
            write(100);
            awaitValue(100);
            int oldLeader = leader();
            isolated = oldLeader;
            long divergentIndex = stores[oldLeader].getLastLogIndex() + 1;
            CompletableFuture<Status> abandoned = new CompletableFuture<>();
            services[oldLeader].getRaftNode().apply(new Task(
                ByteBuffer.allocate(4).putInt(999).flip(), abandoned::complete));
            // The storage API reads the published, forced file record, not LogManager's cache.
            await(() -> {
                LogEntry entry = stores[oldLeader].getEntry(divergentIndex);
                return entry != null && entry.getType() == EntryType.ENTRY_TYPE_DATA
                    && entry.getData().getInt() == 999;
            }, "isolated leader did not persist its uncommitted entry");
            long divergentTerm = stores[oldLeader].getTerm(divergentIndex);
            assertEquals(100, machines[oldLeader].value);
            write(101);
            awaitValue(101);
            isolated = -1;
            awaitValue(101);
            await(() -> stores[oldLeader].getTerm(divergentIndex) > divergentTerm,
                "old leader retained the divergent suffix");
            assertFalse(abandoned.get(20, TimeUnit.SECONDS).isOk());
            write(102);
            awaitValue(102);
            assertLogsAgree();
        }
        
        private void conflictingSnapshotResetsAndRecovers(boolean restart,
            boolean staleConfiguration)
            throws Exception {
            write(300);
            awaitValue(300);
            int follower = leader();
            // Keep the old leader from campaigning during snapshot preparation and inspection.
            ((NodeImpl) services[follower].getRaftNode()).resetElectionTimeoutMs(30_000);
            isolated = follower;
            long firstDivergent = stores[follower].getLastLogIndex() + 1;
            for (int value = 0; value < 40; value++) {
                services[follower].getRaftNode().apply(new Task(
                    ByteBuffer.allocate(4).putInt(999).flip(), status -> {
                    }));
            }
            long lastDivergent = firstDivergent + 39;
            await(() -> stores[follower].getLastLogIndex() == lastDivergent,
                "isolated leader did not persist its uncommitted suffix");
            long staleIndex = -1;
            if (staleConfiguration) {
                // Removing an existing peer appends a real configuration without a catch-up phase.
                List<PeerId> stalePeers = new ArrayList<>(configuration);
                stalePeers.remove(peers.get((follower + 1) % 3));
                services[follower].getRaftNode().changePeers(new Configuration(stalePeers),
                    status -> {
                    });
                await(() -> stores[follower].getLastLogIndex() > lastDivergent
                    && stores[follower].getEntry(stores[follower].getLastLogIndex())
                        .getType() == EntryType.ENTRY_TYPE_CONFIGURATION,
                    "isolated leader did not append its uncommitted configuration");
                staleIndex = stores[follower].getLastLogIndex();
            }
            assertEquals(300, machines[follower].value);
            for (int value = 301; value <= 308; value++) {
                write(value);
            }
            awaitValue(308);
            snapshot();
            write(309);
            awaitValue(309);
            snapshot();
            int snapshotLeader = leader();
            long snapshotIndex = services[snapshotLeader].getRaftNode().getLastCommittedIndex();
            long snapshotTerm = stores[snapshotLeader].getTerm(snapshotIndex);
            assertTrue(snapshotIndex >= firstDivergent && snapshotIndex <= lastDivergent);
            assertTrue(snapshotTerm > stores[follower].getTerm(snapshotIndex));
            assertEquals(999, stores[follower].getEntry(snapshotIndex).getData().getInt());
            await(() -> stores[snapshotLeader].getFirstLogIndex() > firstDivergent,
                "leader did not reclaim the conflicting prefix before snapshot installation");
            // Probes and InstallSnapshot remain real RPCs. Data appends cannot hide reset output.
            pausedAppends = follower;
            isolated = -1;
            await(() -> resetCalls.get(follower) == 1,
                "InstallSnapshot did not invoke the conflicting-term LogStorage reset");
            await(() -> machines[follower].snapshotLoads > 0 && machines[follower].value == 309,
                "conflicting snapshot was not applied");
            SnapshotMeta installed = machines[follower].loadedSnapshot;
            assertEquals(installMetadata[follower], installed);
            assertEquals(snapshotIndex, installed.getLastIncludedIndex());
            assertEquals(snapshotTerm, installed.getLastIncludedTerm());
            assertEquals(new HashSet<>(configuration.stream().map(PeerId::toString)
                .collect(Collectors.toList())), new HashSet<>(installed.getPeersList()));
            assertTrue(installed.getOldPeersList().isEmpty());
            assertTrue(installed.getLearnersList().isEmpty());
            assertTrue(installed.getOldLearnersList().isEmpty());
            assertEquals(snapshotIndex + 1, resetIndexes.get(follower));
            assertEmptyReset(follower, snapshotIndex, lastDivergent);
            assertMembership();
            if (staleConfiguration) {
                assertNull(stores[follower].getEntry(staleIndex));
                assertEquals(0, stores[follower].getTerm(staleIndex));
            }
            if (restart) {
                // Shutdown may transfer leadership via TimeoutNow even with data appends paused.
                // Isolate votes and transfer RPCs as well until empty recovery is inspected.
                isolated = follower;
                for (int index = 0; index < services.length; index++) {
                    stop(index);
                }
                // With quorum stopped, restart cannot repopulate the reset store before inspection.
                start(follower);
                assertEquals(installed, machines[follower].loadedSnapshot);
                assertEquals(309, machines[follower].value);
                assertEmptyReset(follower, snapshotIndex, lastDivergent);
                assertRecoveredSnapshot(follower, installed);
                for (int index = 0; index < 3; index++) {
                    if (index != follower) {
                        start(index);
                    }
                }
            }
            isolated = -1;
            pausedAppends = -1;
            int elected = leader();
            if (elected != follower) {
                // Caught-up voting and leadership must work using the restored snapshot boundary.
                transferLeadership(follower);
            }
            write(310);
            awaitValue(310);
            assertMembership();
            assertLogsAgree();
            assertEquals(1, resetCalls.get(follower));
            if (staleConfiguration) {
                start(3);
                CompletableFuture<Status> added = new CompletableFuture<>();
                services[leader()].getRaftNode().addPeer(peers.get(3), added::complete);
                Status status = added.get(20, TimeUnit.SECONDS);
                assertTrue(status.isOk(), status.toString());
                configuration = new ArrayList<>(peers);
                write(311);
                awaitValue(311);
                assertMembership();
                restartMembers();
                write(312);
                awaitValue(312);
                assertMembership();
                assertLogsAgree();
            }
        }
        
        private void transferLeadership(int target) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            Status last = Status.OK();
            while (System.nanoTime() < deadline) {
                int current = findLeader();
                if (current == target) {
                    return;
                }
                if (current >= 0) {
                    Node leaderNode = services[current].getRaftNode();
                    Node targetNode = services[target].getRaftNode();
                    if (targetNode.getLastLogIndex() == leaderNode.getLastLogIndex()
                        && targetNode.getLastAppliedLogIndex() >= leaderNode.getLastCommittedIndex()
                        && new HashSet<>(leaderNode.listPeers())
                            .equals(new HashSet<>(configuration))
                        && new HashSet<>(((NodeImpl) targetNode).getCurrentConf().listPeers())
                            .equals(new HashSet<>(configuration))) {
                        last = leaderNode.transferLeadershipTo(peers.get(target));
                        if (last.isOk()) {
                            awaitLeadership(() -> services[target].getRaftNode().isLeader(),
                                peers.get(target), last,
                                () -> "expectedPeers=" + configuration + "\n" + diagnostics(),
                                TimeUnit.SECONDS.toNanos(20));
                            return;
                        }
                        assertEquals(RaftError.EBUSY.getNumber(), last.getCode(),
                            last + " " + diagnostics());
                    }
                }
                Thread.sleep(100);
            }
            throw new AssertionError(
                "Leadership transfer timed out: " + last + " " + diagnostics());
        }
        
        private String diagnostics() {
            StringWriter result = new StringWriter();
            PrintWriter writer = new PrintWriter(result);
            for (RaftGroupService service : services) {
                if (service != null) {
                    Node node = service.getRaftNode();
                    writer.println(node.getNodeId() + " last=" + node.getLastLogIndex()
                        + " committed=" + node.getLastCommittedIndex() + " applied="
                        + node.getLastAppliedLogIndex());
                    node.describe(new Describer.DefaultPrinter(writer));
                }
            }
            writer.flush();
            return result.toString();
        }
        
        private void assertEmptyReset(int member, long snapshotIndex, long discardedLast) {
            assertEquals(snapshotIndex + 1, stores[member].getFirstLogIndex());
            assertEquals(snapshotIndex, stores[member].getLastLogIndex());
            assertNull(stores[member].getEntry(snapshotIndex));
            assertEquals(0, stores[member].getTerm(snapshotIndex));
            for (long index = snapshotIndex + 1; index <= discardedLast; index++) {
                assertNull(stores[member].getEntry(index));
                assertEquals(0, stores[member].getTerm(index));
            }
        }
        
        private void assertRecoveredSnapshot(int member, SnapshotMeta snapshot) throws Exception {
            Field field = NodeImpl.class.getDeclaredField("logManager");
            field.setAccessible(true);
            LogManager manager = (LogManager) field.get(services[member].getRaftNode());
            assertTrue(manager.checkConsistency().isOk());
            assertEquals(snapshot.getLastIncludedIndex(), manager.getLastLogIndex());
            assertEquals(snapshot.getLastIncludedTerm(),
                manager.getTerm(snapshot.getLastIncludedIndex()));
            ConfigurationEntry recovered =
                manager.getConfiguration(snapshot.getLastIncludedIndex());
            assertEquals(snapshot.getLastIncludedIndex(), recovered.getId().getIndex());
            assertEquals(snapshot.getLastIncludedTerm(), recovered.getId().getTerm());
            assertEquals(new HashSet<>(configuration),
                new HashSet<>(recovered.getConf().listPeers()));
            assertTrue(recovered.getConf().listLearners().isEmpty());
            assertTrue(recovered.getOldConf().isEmpty());
        }
        
        private void membershipSurvivesSnapshots() throws Exception {
            start(3);
            CompletableFuture<Status> added = new CompletableFuture<>();
            services[leader()].getRaftNode().addPeer(peers.get(3), added::complete);
            assertTrue(added.get(20, TimeUnit.SECONDS).isOk());
            int removed = (leader() + 1) % 3;
            CompletableFuture<Status> deleted = new CompletableFuture<>();
            services[leader()].getRaftNode().removePeer(peers.get(removed), deleted::complete);
            assertTrue(deleted.get(20, TimeUnit.SECONDS).isOk());
            configuration = new ArrayList<>(peers);
            configuration.remove(peers.get(removed));
            stop(removed);
            write(200);
            awaitValue(200);
            assertMembership();
            long configurationIndex = latestConfigurationIndex(stores[leader()]);
            assertTrue(configurationIndex > 0);
            restartMembers();
            write(201);
            awaitValue(201);
            assertMembership();
            assertTrue(latestConfigurationIndex(stores[leader()]) >= configurationIndex);
            snapshotMembers();
            write(202);
            awaitValue(202);
            snapshotMembers();
            await(() -> java.util.stream.IntStream.range(0, services.length)
                .filter(index -> services[index] != null)
                .allMatch(index -> stores[index].getFirstLogIndex() > configurationIndex),
                "snapshots did not reclaim the configuration log");
            restartMembers();
            await(() -> java.util.stream.IntStream.range(0, services.length)
                .filter(index -> services[index] != null)
                .allMatch(index -> machines[index].snapshotLoads > 0),
                "members did not restore snapshots");
            for (int member = 0; member < services.length; member++) {
                if (services[member] != null) {
                    SnapshotMeta metadata = machines[member].loadedSnapshot;
                    assertEquals(new HashSet<>(configuration.stream().map(PeerId::toString)
                        .collect(Collectors.toList())), new HashSet<>(metadata.getPeersList()));
                    assertTrue(metadata.getOldPeersList().isEmpty());
                    assertTrue(metadata.getLearnersList().isEmpty());
                    assertTrue(metadata.getOldLearnersList().isEmpty());
                    assertTrue(metadata.getLastIncludedIndex() >= configurationIndex);
                    assertTrue(metadata.getLastIncludedTerm() > 0);
                }
            }
            write(203);
            awaitValue(203);
            assertMembership();
            assertLogsAgree();
        }
        
        private long latestConfigurationIndex(FileLogStorage store) {
            for (long index = store.getLastLogIndex(); index >= store.getFirstLogIndex(); index--) {
                LogEntry entry = store.getEntry(index);
                if (entry != null && entry.getType() == EntryType.ENTRY_TYPE_CONFIGURATION) {
                    assertEquals(new HashSet<>(configuration), new HashSet<>(entry.getPeers()));
                    return index;
                }
            }
            return 0;
        }
        
        private void snapshotMembers() throws Exception {
            for (RaftGroupService service : services) {
                if (service != null) {
                    CompletableFuture<Status> result = new CompletableFuture<>();
                    service.getRaftNode().snapshot(result::complete);
                    assertTrue(result.get(20, TimeUnit.SECONDS).isOk());
                }
            }
        }
        
        private void restartMembers() throws Exception {
            for (int index = 0; index < services.length; index++) {
                stop(index);
            }
            for (int index = 0; index < services.length; index++) {
                if (configuration.contains(peers.get(index))) {
                    start(index);
                }
            }
            leader();
        }
        
        private void assertMembership() throws Exception {
            assertEquals(new HashSet<>(configuration),
                new HashSet<>(services[leader()].getRaftNode().listPeers()));
            Field field = NodeImpl.class.getDeclaredField("logManager");
            field.setAccessible(true);
            for (RaftGroupService service : services) {
                if (service != null) {
                    Node node = service.getRaftNode();
                    LogManager manager = (LogManager) field.get(node);
                    ConfigurationEntry recovered =
                        manager.getConfiguration(node.getLastCommittedIndex());
                    assertEquals(new HashSet<>(configuration),
                        new HashSet<>(recovered.getConf().listPeers()));
                    assertTrue(recovered.getOldConf().isEmpty());
                    assertTrue(recovered.getId().getIndex() > 0);
                    assertTrue(recovered.getId().getTerm() > 0);
                }
            }
        }
        
        private void assertLogsAgree() throws Exception {
            int currentLeader = leader();
            long last = stores[currentLeader].getLastLogIndex();
            await(() -> java.util.stream.IntStream.range(0, services.length)
                .filter(index -> services[index] != null)
                .allMatch(index -> stores[index].getLastLogIndex() == last),
                "published log ranges did not converge");
            for (int member = 0; member < services.length; member++) {
                if (services[member] == null) {
                    continue;
                }
                long first = Math.max(stores[currentLeader].getFirstLogIndex(),
                    stores[member].getFirstLogIndex());
                assertTrue(first <= last,
                    "Expected retained logs after a write: member=" + member
                        + ", first=" + first + ", last=" + last);
                for (long index = first; index <= last; index++) {
                    String context = "leader=" + currentLeader + ", member=" + member
                        + ", index=" + index;
                    LogEntry expected = stores[currentLeader].getEntry(index);
                    LogEntry actual = stores[member].getEntry(index);
                    assertNotNull(expected, "Leader log is unreadable: " + context);
                    assertNotNull(actual, "Member log is unreadable: " + context);
                    assertEquals(index, expected.getId().getIndex(), context);
                    assertEquals(index, actual.getId().getIndex(), context);
                    assertEquals(expected, actual, context);
                }
            }
        }
        
        private final class PartitionServer implements RpcServer {
            
            private final RpcServer delegate;
            
            private final int local;
            
            private PartitionServer(RpcServer delegate, int local) {
                this.delegate = delegate;
                this.local = local;
            }
            
            @Override
            public boolean init(Void options) {
                return delegate.init(options);
            }
            
            @Override
            public void shutdown() {
                delegate.shutdown();
            }
            
            @Override
            public int boundPort() {
                return delegate.boundPort();
            }
            
            @Override
            public boolean isStarted() {
                return delegate.isStarted();
            }
            
            @Override
            public void registerConnectionClosedEventListener(
                ConnectionClosedEventListener listener) {
                delegate.registerConnectionClosedEventListener(listener);
            }
            
            @Override
            public void registerProcessor(RpcProcessor<?> processor) {
                registerTyped(processor);
            }
            
            private <T> void registerTyped(RpcProcessor<T> processor) {
                delegate.registerProcessor(new RpcProcessor<T>() {
                    
                    @Override
                    public void handleRequest(RpcContext context, T request) {
                        String sender = sender(request);
                        int partition = isolated;
                        if (partition >= 0 && sender != null
                            && (local == partition
                                || sender.equals(peers.get(partition).toString()))) {
                            context.sendResponse(RpcRequests.ErrorResponse.newBuilder()
                                .setErrorCode(16).setErrorMsg("test network partition").build());
                            return;
                        }
                        if (local == pausedAppends
                            && request instanceof RpcRequests.AppendEntriesRequest
                            && ((RpcRequests.AppendEntriesRequest) request).getEntriesCount() > 0) {
                            context.sendResponse(RpcRequests.ErrorResponse.newBuilder()
                                .setErrorCode(16).setErrorMsg("test pauses data replication")
                                .build());
                            return;
                        }
                        if (request instanceof RpcRequests.InstallSnapshotRequest) {
                            installMetadata[local] =
                                ((RpcRequests.InstallSnapshotRequest) request).getMeta();
                        }
                        processor.handleRequest(context, request);
                    }
                    
                    @Override
                    public String interest() {
                        return processor.interest();
                    }
                    
                    @Override
                    public Executor executor() {
                        return processor.executor();
                    }
                    
                    @Override
                    public ExecutorSelector executorSelector() {
                        return processor.executorSelector();
                    }
                });
            }
            
            private String sender(Object request) {
                if (request instanceof RpcRequests.AppendEntriesRequest) {
                    return ((RpcRequests.AppendEntriesRequest) request).getServerId();
                }
                if (request instanceof RpcRequests.RequestVoteRequest) {
                    return ((RpcRequests.RequestVoteRequest) request).getServerId();
                }
                if (request instanceof RpcRequests.InstallSnapshotRequest) {
                    return ((RpcRequests.InstallSnapshotRequest) request).getServerId();
                }
                if (request instanceof RpcRequests.TimeoutNowRequest) {
                    return ((RpcRequests.TimeoutNowRequest) request).getServerId();
                }
                return null;
            }
        }
        
        private void stop(int index) throws InterruptedException {
            if (services[index] != null) {
                services[index].shutdown();
                services[index].join();
                services[index] = null;
            }
        }
        
        @Override
        public void close() throws InterruptedException {
            Throwable failure = null;
            boolean interrupted = false;
            for (int index = 0; index < services.length; index++) {
                try {
                    stop(index);
                } catch (InterruptedException | RuntimeException | AssertionError e) {
                    interrupted |= e instanceof InterruptedException;
                    if (failure == null) {
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
            if (failure instanceof InterruptedException) {
                throw (InterruptedException) failure;
            }
            if (failure instanceof RuntimeException) {
                throw (RuntimeException) failure;
            }
            if (failure != null) {
                throw (AssertionError) failure;
            }
        }
    }
    
    private static final class Machine extends StateMachineAdapter {
        
        private volatile int value;
        
        private volatile int snapshotLoads;
        
        private volatile SnapshotMeta loadedSnapshot;
        
        @Override
        public void onApply(Iterator iterator) {
            while (iterator.hasNext()) {
                value = iterator.getData().getInt();
                if (iterator.done() != null) {
                    iterator.done().run(Status.OK());
                }
                iterator.next();
            }
        }
        
        @Override
        public void onSnapshotSave(SnapshotWriter writer, Closure done) {
            try {
                Files.writeString(Path.of(writer.getPath(), "state"), Integer.toString(value));
                assertTrue(writer.addFile("state"));
                done.run(Status.OK());
            } catch (IOException e) {
                done.run(new Status(-1, e.toString()));
            }
        }
        
        @Override
        public boolean onSnapshotLoad(SnapshotReader reader) {
            try {
                value = Integer.parseInt(Files.readString(Path.of(reader.getPath(), "state")));
                loadedSnapshot = reader.load();
                snapshotLoads++;
                return true;
            } catch (IOException e) {
                return false;
            }
        }
    }
}
