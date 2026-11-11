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

import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Iterator;
import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.RaftGroupService;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.core.StateMachineAdapter;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.entity.Task;
import com.alipay.sofa.jraft.option.NodeOptions;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotReader;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotWriter;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each voting node owns its JVM, RPC server and real durable files.
 * Failed tests retain the temporary directory containing node files and process logs.
 */
public class FileRaftProcessTest {
    
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    Path directory;
    
    @ParameterizedTest
    @ValueSource(strings = {"leader", "follower"})
    void acknowledgedCommandsSurviveProcessDeath(String victimRole) throws Exception {
        try (Processes cluster = new Processes(directory)) {
            cluster.startAll();
            cluster.write(1);
            cluster.write(2);
            cluster.awaitCommands(List.of(1, 2));
            int leader = cluster.leader();
            int victim = "leader".equals(victimRole) ? leader : (leader + 1) % 3;
            cluster.kill(victim);
            cluster.write(3);
            cluster.write(4);
            cluster.start(victim);
            cluster.awaitCommands(List.of(1, 2, 3, 4));
            cluster.write(5);
            cluster.awaitCommands(List.of(1, 2, 3, 4, 5));
            // Preserve a snapshot and then logs after it, not just the last applied value.
            for (Member member : cluster.members) {
                assertEquals("OK", member.request("SNAPSHOT"));
            }
            cluster.write(6);
            cluster.awaitCommands(List.of(1, 2, 3, 4, 5, 6));
            cluster.stopAll();
            cluster.startAll();
            cluster.awaitCommands(List.of(1, 2, 3, 4, 5, 6));
            for (Member member : cluster.members) {
                assertTrue(Integer.parseInt(member.request("STATE").split("\\|", -1)[4]) > 0,
                    "Restart did not load its persisted snapshot: " + member.log);
            }
            cluster.write(7);
            cluster.awaitCommands(List.of(1, 2, 3, 4, 5, 6, 7));
        }
    }
    
    private static final class Processes implements AutoCloseable {
        
        private final Path root;
        
        private final Member[] members = new Member[3];
        
        private final List<Integer> ports = new ArrayList<>();
        
        private final String peers;
        
        private Processes(Path root) throws IOException {
            this.root = root;
            List<ServerSocket> reservations = new ArrayList<>();
            try {
                for (int i = 0; i < 3; i++) {
                    ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                    reservations.add(socket);
                    ports.add(socket.getLocalPort());
                }
                peers = String.join(",", ports.stream().map(port -> "127.0.0.1:" + port).toList());
            } finally {
                for (ServerSocket socket : reservations) {
                    socket.close();
                }
            }
        }
        
        private void start(int index) throws Exception {
            Path home = Files.createDirectories(root.resolve("node-" + index));
            try (ServerSocket rendezvous =
                new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
                rendezvous.setSoTimeout(30_000);
                Path log = home.resolve("process-" + System.nanoTime() + ".log");
                Process child = RaftProcessSupport.start(FileRaftProcessTest.class, log,
                    home.toString(), ports.get(index).toString(), peers,
                    Integer.toString(rendezvous.getLocalPort()));
                Socket control = null;
                try {
                    control = rendezvous.accept();
                    Member member = new Member(child, control, log);
                    assertEquals("READY", member.input.readUTF());
                    members[index] = member;
                } catch (Exception | AssertionError e) {
                    try {
                        terminate(child, control);
                    } catch (Exception | AssertionError cleanup) {
                        e.addSuppressed(cleanup);
                    }
                    throw new AssertionError("Node failed to start: " + log, e);
                }
            }
        }
        
        private void startAll() throws Exception {
            for (int i = 0; i < members.length; i++) {
                start(i);
            }
        }
        
        private int leader() throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (System.nanoTime() < deadline) {
                for (int i = 0; i < members.length; i++) {
                    if (members[i] != null) {
                        try {
                            if (members[i].request("STATE", deadline).startsWith("true|")) {
                                return i;
                            }
                        } catch (IOException e) {
                            throw new AssertionError("Leader query failed: " + diagnostics(), e);
                        }
                    }
                }
                Thread.sleep(50);
            }
            throw new AssertionError("No leader: " + diagnostics());
        }
        
        private void write(int command) throws Exception {
            assertEquals("OK", members[leader()].request("WRITE " + command), this::diagnostics);
        }
        
        private void awaitCommands(List<Integer> expected) throws Exception {
            String commands = expected.toString();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (System.nanoTime() < deadline) {
                boolean complete = true;
                for (Member member : members) {
                    if (member == null) {
                        continue;
                    }
                    String[] state;
                    try {
                        state = member.request("STATE", deadline).split("\\|", -1);
                    } catch (IOException e) {
                        throw new AssertionError("Expected " + commands + ": " + diagnostics(), e);
                    }
                    complete &= state[3].equals(commands)
                        && Long.parseLong(state[2]) >= Long.parseLong(state[1]);
                }
                if (complete) {
                    return;
                }
                Thread.sleep(50);
            }
            throw new AssertionError("Expected " + commands + ": " + diagnostics());
        }
        
        private String diagnostics() {
            StringBuilder result = new StringBuilder();
            for (Member member : members) {
                if (member != null) {
                    result.append(member.diagnostics()).append('\n');
                }
            }
            return result.toString();
        }
        
        private void kill(int index) throws Exception {
            Member member = members[index];
            terminate(member.child, member.socket);
            assertFalse(member.child.isAlive());
            members[index] = null;
            RaftProcessSupport.assertNoRocksDb(member.log);
        }
        
        private void stopAll() throws Exception {
            stopMembers(members, root);
        }
        
        @Override
        public void close() throws Exception {
            stopAll();
        }
    }
    
    static void stopMembers(Member[] members, Path root) throws Exception {
        List<Throwable> failures = new ArrayList<>();
        for (int i = 0; i < members.length; i++) {
            Member member = members[i];
            if (member == null) {
                continue;
            }
            try {
                assertEquals("OK", member.request("STOP"));
                assertTrue(member.child.waitFor(15, TimeUnit.SECONDS), member.log.toString());
                assertEquals(0, member.child.exitValue(), Files.readString(member.log));
                RaftProcessSupport.assertNoRocksDb(member.log);
            } catch (Exception | AssertionError e) {
                failures.add(e);
            } finally {
                try {
                    terminate(member.child, member.socket);
                } catch (Exception | AssertionError e) {
                    failures.add(e);
                } finally {
                    members[i] = null;
                }
            }
        }
        if (!failures.isEmpty()) {
            AssertionError failure = new AssertionError("Failed to stop Raft members in "
                + root, failures.get(0));
            failures.stream().skip(1).forEach(failure::addSuppressed);
            if (failures.stream().anyMatch(InterruptedException.class::isInstance)) {
                Thread.currentThread().interrupt();
            }
            throw failure;
        }
    }
    
    private static void terminate(Process child, Socket socket) throws Exception {
        try (socket) {
            child.destroyForcibly();
            assertTrue(child.waitFor(10, TimeUnit.SECONDS), "Raft child did not terminate");
        }
    }
    
    static final class Member {
        
        private final Process child;
        
        private final Socket socket;
        
        private final DataInputStream input;
        
        private final DataOutputStream output;
        
        private final Path log;
        
        private String lastState = "<not sampled>";
        
        private final LongSupplier nanoTime;
        
        private long deadline;
        
        Member(Process child, Socket socket, Path log) throws IOException {
            this(child, socket, log, System::nanoTime);
        }
        
        Member(Process child, Socket socket, Path log, LongSupplier nanoTime) throws IOException {
            this.child = child;
            this.socket = socket;
            this.log = log;
            this.nanoTime = nanoTime;
            deadline = nanoTime.getAsLong() + TimeUnit.SECONDS.toNanos(30);
            socket.setSoTimeout(30_000);
            input = new DataInputStream(new FilterInputStream(socket.getInputStream()) {
                
                @Override
                public int read() throws IOException {
                    socket.setSoTimeout(remainingMillis());
                    return super.read();
                }
                
                @Override
                public int read(byte[] bytes, int offset, int length) throws IOException {
                    socket.setSoTimeout(remainingMillis());
                    return in.read(bytes, offset, length);
                }
            });
            output = new DataOutputStream(socket.getOutputStream());
        }
        
        String request(String command) throws IOException {
            return request(command, nanoTime.getAsLong() + TimeUnit.SECONDS.toNanos(30));
        }
        
        String request(String command, long requestDeadline) throws IOException {
            deadline = requestDeadline;
            try {
                socket.setSoTimeout(remainingMillis());
                output.writeUTF(command);
                output.flush();
                String response = input.readUTF();
                if ("STATE".equals(command)) {
                    lastState = response;
                }
                return response;
            } catch (IOException e) {
                // A late response must not be consumed by a subsequent control request.
                try {
                    socket.close();
                } catch (IOException cleanup) {
                    e.addSuppressed(cleanup);
                }
                throw e;
            }
        }
        
        private int remainingMillis() throws SocketTimeoutException {
            long remaining = deadline - nanoTime.getAsLong();
            if (remaining <= 0) {
                throw new SocketTimeoutException("Control request deadline expired: " + log);
            }
            return (int) Math.min(Integer.MAX_VALUE,
                1 + TimeUnit.NANOSECONDS.toMillis(remaining - 1));
        }
        
        String diagnostics() {
            return log + ": alive=" + child.isAlive() + ", lastState=" + lastState;
        }
    }
    
    /**
     * Runs one real voting node controlled by the parent process.
     * @param args home, Raft port, peers, control port
     * @throws Exception on startup, I/O or Raft failure
     */
    public static void main(String[] args) throws Exception {
        Path home = Path.of(args[0]);
        final PeerId self = PeerId.parsePeer("127.0.0.1:" + args[1]);
        Configuration configuration = new Configuration();
        assertTrue(configuration.parse(args[2]));
        Commands machine = new Commands();
        NodeOptions options = new NodeOptions();
        options.setServiceFactory(new FileJRaftServiceFactory());
        options.setInitialConf(configuration);
        options.setElectionTimeoutMs(1500);
        options.setLogUri(home.resolve("log").toString());
        options.setRaftMetaUri(home.resolve("meta").toString());
        options.setSnapshotUri(home.resolve("snapshot").toString());
        options.setSnapshotIntervalSecs(0);
        options.setSnapshotLogIndexMargin(0);
        options.getRaftOptions().setSync(true);
        options.setRaftRpcThreadPoolSize(2);
        options.setCliRpcThreadPoolSize(1);
        options.setFsm(machine);
        RaftGroupService service = new RaftGroupService("process-cluster", self, options);
        Node node = service.start();
        try (Socket socket =
            new Socket(InetAddress.getLoopbackAddress(), Integer.parseInt(args[3]))) {
            DataInputStream input = new DataInputStream(socket.getInputStream());
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.writeUTF("READY");
            output.flush();
            boolean running = true;
            while (running) {
                String command = input.readUTF();
                String result;
                if ("STATE".equals(command)) {
                    result = node.isLeader() + "|" + node.getLastCommittedIndex() + "|"
                        + node.getLastAppliedLogIndex() + "|" + machine.copy() + "|"
                        + machine.snapshotLoads;
                } else if (command.startsWith("WRITE ")) {
                    CompletableFuture<Status> done = new CompletableFuture<>();
                    node.apply(new Task(ByteBuffer.allocate(4)
                        .putInt(Integer.parseInt(command.substring(6))).flip(), done::complete));
                    Status status = done.get(20, TimeUnit.SECONDS);
                    result = status.isOk() ? "OK" : status.toString();
                } else if ("SNAPSHOT".equals(command)) {
                    CompletableFuture<Status> done = new CompletableFuture<>();
                    node.snapshot(done::complete);
                    Status status = done.get(20, TimeUnit.SECONDS);
                    result = status.isOk() ? "OK" : status.toString();
                } else if ("STOP".equals(command)) {
                    running = false;
                    result = "OK";
                } else {
                    throw new IllegalArgumentException(command);
                }
                output.writeUTF(result);
                output.flush();
            }
        } finally {
            service.shutdown();
            service.join();
        }
        System.exit(0);
    }
    
    private static final class Commands extends StateMachineAdapter {
        
        private final List<Integer> commands = new ArrayList<>();
        
        private volatile int snapshotLoads;
        
        private synchronized List<Integer> copy() {
            return new ArrayList<>(commands);
        }
        
        @Override
        public synchronized void onApply(Iterator iterator) {
            while (iterator.hasNext()) {
                commands.add(iterator.getData().getInt());
                if (iterator.done() != null) {
                    iterator.done().run(Status.OK());
                }
                iterator.next();
            }
        }
        
        @Override
        public void onSnapshotSave(SnapshotWriter writer, Closure done) {
            try {
                Files.write(Path.of(writer.getPath(), "commands"),
                    copy().stream().map(String::valueOf).toList());
                assertTrue(writer.addFile("commands"));
                done.run(Status.OK());
            } catch (IOException e) {
                done.run(new Status(-1, e.toString()));
            }
        }
        
        @Override
        public synchronized boolean onSnapshotLoad(SnapshotReader reader) {
            try {
                commands.clear();
                for (String command : Files.readAllLines(Path.of(reader.getPath(), "commands"))) {
                    commands.add(Integer.parseInt(command));
                }
                snapshotLoads++;
                return true;
            } catch (IOException e) {
                return false;
            }
        }
    }
}
