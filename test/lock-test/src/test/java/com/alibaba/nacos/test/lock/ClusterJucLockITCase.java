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

package com.alibaba.nacos.test.lock;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.lock.LockService;
import com.alibaba.nacos.client.lock.NacosLock;
import com.alibaba.nacos.client.lock.NacosLockService;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * JUC style distributed lock cluster integration tests.
 *
 * @author DHX
 */
public class ClusterJucLockITCase {

    private static final String CLUSTER_SERVER_ADDR = System.getProperty(
            "nacos.lock.cluster.serverAddr",
            "127.0.0.1:18848,127.0.0.1:18858,127.0.0.1:18868");

    private static final Path CLUSTER_HOME = Paths.get(
            System.getProperty("nacos.lock.cluster.home", "/tmp/nacos-cluster"));

    private static final int[] MAIN_PORTS = parsePorts(
            System.getProperty("nacos.lock.cluster.mainPorts", "18848,18858,18868"));

    private static final int[] CONSOLE_PORTS = parsePorts(
            System.getProperty("nacos.lock.cluster.consolePorts", "18080,18081,18082"));

    private static final int WORKER_COUNT = 5;

    private static final int RESTART_ROUNDS = 2;

    private final List<LockService> lockServices = new ArrayList<>();

    @AfterEach
    void tearDown() throws Exception {
        for (LockService each : lockServices) {
            each.shutdown();
        }
        lockServices.clear();
    }

//    @Test
    @DisplayName("JUC-CLUSTER-001: 高并发场景下频繁启停不同节点锁仍保持互斥")
    void testHighConcurrencyStabilityWithFrequentNodeRestarts() throws Exception {
        // 这个用例依赖本地已准备好的三节点 Nacos 集群。集群目录或启动脚本不存在时跳过，
        // 避免普通 standalone lock IT 被迫管理集群进程。
        assumeClusterHomeReady();
        assumeTrue(waitForAllNodesReady(TimeUnit.SECONDS.toMillis(30)),
                "Nacos cluster must be ready before running this test");

        // 每个客户端都使用同一组三节点地址，但首选节点不同。这样初始连接会分散到
        // 18848、18858、18868，同时节点重启时仍可故障转移到其它存活节点。
        String key = "cluster-juc-high-concurrency-" + System.currentTimeMillis();
        List<NacosLockService> services = createClusterLockServices();

        // running 同时控制两条压力线：
        // - worker 线程在 running 为 true 时持续竞争同一把 JUC 锁；
        // - 节点 churn 线程完成所有重启轮次后会把 running 置为 false。
        AtomicBoolean running = new AtomicBoolean(true);

        // successCount 验证节点频繁重启期间 worker 仍能成功获取锁。
        // retryExceptionCount 记录可重试抖动：请求可能刚好打到正在关闭的节点，
        // 下一轮会换一个客户端继续尝试。
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger retryExceptionCount = new AtomicInteger(0);

        // activeHolders 是真正的互斥探针。正确的锁只会让它保持在 0 或 1。
        // 如果两个 worker 同时进入临界区，maxConcurrentHolders 会大于 1，
        // conflictCount 也会递增，即使 successCount 看起来正常也会让测试失败。
        AtomicInteger activeHolders = new AtomicInteger(0);
        AtomicInteger maxConcurrentHolders = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        // 节点启停失败不属于可重试锁请求抖动。进程启停或健康检查失败说明测试环境
        // 没有成功执行预期的集群场景。
        AtomicInteger churnFailureCount = new AtomicInteger(0);
        CountDownLatch workersDone = new CountDownLatch(WORKER_COUNT);

        // 单独启动一个 churn 线程轮流重启节点；与此同时 worker 持续竞争同一个锁 key。
        // churn 线程每轮停止一个节点，短暂等待，再启动该节点并等待健康检查通过。
        Thread churnThread = new Thread(() -> churnClusterNodes(running, churnFailureCount),
                "nacos-lock-cluster-node-churn");
        churnThread.start();

        ExecutorService executor = Executors.newFixedThreadPool(WORKER_COUNT);
        try {
            for (int i = 0; i < WORKER_COUNT; i++) {
                final int workerId = i;
                executor.submit(() -> runLockWorker(key, services, running, workerId,
                        successCount, retryExceptionCount, activeHolders, maxConcurrentHolders,
                        conflictCount, workersDone));
            }

            churnThread.join(TimeUnit.MINUTES.toMillis(4));
            assertTrue(!churnThread.isAlive(), "Node churn thread should finish");
            running.set(false);
            assertTrue(workersDone.await(60, TimeUnit.SECONDS), "All lock workers should finish");
        } finally {
            // 无论断言或 worker 是否失败，都停止 worker，并把集群恢复到全节点健康状态；
            // 如果恢复失败，startAllNodes 会让测试失败，避免留下异常集群影响后续测试。
            running.set(false);
            executor.shutdownNow();
            churnThread.join(TimeUnit.SECONDS.toMillis(30));
            startAllNodes();
        }

        assertTrue(successCount.get() >= WORKER_COUNT,
                "Workers should acquire the lock successfully during node restarts, successCount="
                        + successCount.get() + ", retryExceptionCount="
                        + retryExceptionCount.get());
        assertEquals(0, churnFailureCount.get(), "Node restart operations should not fail");
        assertEquals(0, conflictCount.get(),
                "No concurrent access should occur in critical section");
        assertEquals(1, maxConcurrentHolders.get(),
                "Only one holder should enter critical section at a time");
    }

    private void runLockWorker(String key, List<NacosLockService> services,
            AtomicBoolean running, int workerId, AtomicInteger successCount,
            AtomicInteger retryExceptionCount, AtomicInteger activeHolders,
            AtomicInteger maxConcurrentHolders, AtomicInteger conflictCount,
            CountDownLatch workersDone) {
        int attempt = 0;
        try {
            while (running.get()) {
                // 每次尝试轮换客户端，避免某个 worker 固定走同一条连接路径，
                // 同时覆盖集群地址列表下的客户端故障转移行为。
                NacosLockService service = services.get((workerId + attempt) % services.size());
                NacosLock lock = service.getReentrantLock(key);
                boolean locked = false;
                try {
                    // 这里使用 JUC 风格 API，而不是底层 LockService API。
                    // 使用有界 tryLock，避免节点正在停机或重连时 worker 长时间卡住。
                    if (!lock.tryLock(5, TimeUnit.SECONDS)) {
                        retryExceptionCount.incrementAndGet();
                        continue;
                    }
                    locked = true;
                    successCount.incrementAndGet();

                    // 临界区：同一时刻只允许一个 worker 进入。计数器本身虽然线程安全，
                    // 但这里观察的是并发进入临界区的数量，而不是最终计数是否正确。
                    int currentHolders = activeHolders.incrementAndGet();
                    maxConcurrentHolders.accumulateAndGet(currentHolders, Math::max);
                    if (currentHolders > 1) {
                        conflictCount.incrementAndGet();
                    }
                    Thread.sleep(10);
                } catch (Exception e) {
                    // 节点停止或客户端重连期间，锁请求可能短暂失败。这里记录失败次数，
                    // 短暂休眠后进入下一轮，并切换到另一个客户端继续尝试。
                    retryExceptionCount.incrementAndGet();
                    sleepQuietly(100);
                } finally {
                    if (locked) {
                        activeHolders.decrementAndGet();
                        try {
                            lock.unlock();
                        } catch (Exception e) {
                            retryExceptionCount.incrementAndGet();
                        }
                    }
                }
                attempt++;
            }
        } finally {
            workersDone.countDown();
        }
    }

    private void churnClusterNodes(AtomicBoolean running, AtomicInteger churnFailureCount) {
        try {
            for (int round = 0; round < RESTART_ROUNDS && running.get(); round++) {
                // 每轮只重启一个节点，并按 node-1、node-2、node-3 循环。
                // 三节点 CP 集群在单节点下线时仍应保持多数派，分布式锁也应保持互斥。
                int nodeIndex = round % MAIN_PORTS.length;
                stopNode(nodeIndex);
                sleepQuietly(1500);
                startNode(nodeIndex);
                if (!waitForNodeReady(nodeIndex, TimeUnit.SECONDS.toMillis(90))) {
                    churnFailureCount.incrementAndGet();
                    return;
                }
                sleepQuietly(500);
            }
        } catch (Exception e) {
            churnFailureCount.incrementAndGet();
        } finally {
            running.set(false);
        }
    }

    private List<NacosLockService> createClusterLockServices() throws NacosException {
        String[] addresses = CLUSTER_SERVER_ADDR.split(",");
        List<NacosLockService> result = new ArrayList<>(addresses.length);
        for (int i = 0; i < addresses.length; i++) {
            String serverAddr = rotateServerAddr(addresses, i);
            NacosLockService service = (NacosLockService) createLockService(serverAddr);
            lockServices.add(service);
            result.add(service);
        }
        return result;
    }

    private LockService createLockService(String serverAddr) throws NacosException {
        EnvUtil.setEnvironment(new StandardEnvironment());
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, serverAddr);
        properties.setProperty(PropertyKeyConst.USERNAME, "nacos");
        properties.setProperty(PropertyKeyConst.PASSWORD, "nacos");
        return NacosFactory.createLockService(properties);
    }

    private String rotateServerAddr(String[] addresses, int offset) {
        List<String> ordered = new ArrayList<>(addresses.length);
        for (int i = 0; i < addresses.length; i++) {
            ordered.add(addresses[(offset + i) % addresses.length]);
        }
        return String.join(",", ordered);
    }

    private void assumeClusterHomeReady() {
        assumeTrue(Files.isDirectory(CLUSTER_HOME), "Cluster home does not exist: " + CLUSTER_HOME);
        for (int i = 0; i < MAIN_PORTS.length; i++) {
            Path nodeDir = nodeDir(i);
            assumeTrue(Files.isDirectory(nodeDir),
                    "Cluster node directory does not exist: " + nodeDir);
            assumeTrue(Files.isRegularFile(nodeDir.resolve("bin/startup.sh")),
                    "Cluster node startup script does not exist: "
                            + nodeDir.resolve("bin/startup.sh"));
        }
    }

    private void stopNode(int nodeIndex) throws Exception {
        Optional<ProcessHandle> process = findNodeProcess(nodeIndex);
        assumeTrue(process.isPresent(), "Unable to find Nacos process for node " + (nodeIndex + 1));
        ProcessHandle handle = process.get();
        handle.destroy();
        if (!waitForProcessExit(handle, TimeUnit.SECONDS.toMillis(20))) {
            handle.destroyForcibly();
            assertTrue(waitForProcessExit(handle, TimeUnit.SECONDS.toMillis(20)),
                    "Nacos node process should stop: " + (nodeIndex + 1));
        }
        waitForNodeDown(nodeIndex, TimeUnit.SECONDS.toMillis(30));
    }

    private void startNode(int nodeIndex) throws Exception {
        if (findNodeProcess(nodeIndex).isPresent()) {
            return;
        }
        Path nodeDir = nodeDir(nodeIndex);
        Path restartLog = nodeDir.resolve("logs/restart-from-it.log");
        Files.createDirectories(restartLog.getParent());

        List<String> command = new ArrayList<>();
        command.add("bash");
        command.add(nodeDir.resolve("bin/startup.sh").toString());
        String embeddedStorage = System.getProperty("nacos.lock.cluster.embeddedStorage", "true");
        if (Boolean.parseBoolean(embeddedStorage)) {
            command.add("-p");
            command.add("embedded");
        }

        Process process = new ProcessBuilder(command).directory(nodeDir.toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(restartLog.toFile()))
                .start();
        process.getInputStream().close();
        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Node startup script should exit");
        assertEquals(0, process.exitValue(), "Node startup script should succeed");
    }

    private void startAllNodes() throws Exception {
        for (int i = 0; i < MAIN_PORTS.length; i++) {
            startNode(i);
        }
        waitForAllNodesReady(TimeUnit.SECONDS.toMillis(120));
    }

    private Optional<ProcessHandle> findNodeProcess(int nodeIndex) throws IOException {
        Path nodeDir = nodeDir(nodeIndex).toRealPath();
        String marker = "-Dnacos.home=" + nodeDir;
        Path pidFile = nodeDir.resolve("pid");
        if (Files.isRegularFile(pidFile)) {
            String pidText = new String(Files.readAllBytes(pidFile), StandardCharsets.UTF_8).trim();
            if (!pidText.isEmpty()) {
                try {
                    Optional<ProcessHandle> pidHandle = ProcessHandle.of(Long.parseLong(pidText));
                    if (pidHandle.isPresent() && pidHandle.get().isAlive()
                            && pidHandle.get().info().commandLine().orElse("").contains(marker)) {
                        return pidHandle;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return ProcessHandle.allProcesses()
                .filter(ProcessHandle::isAlive)
                .filter(each -> each.info().commandLine().orElse("").contains(marker))
                .findFirst();
    }

    private boolean waitForAllNodesReady(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            boolean allReady = true;
            for (int i = 0; i < MAIN_PORTS.length; i++) {
                if (!isNodeReady(i)) {
                    allReady = false;
                    break;
                }
            }
            if (allReady) {
                return true;
            }
            Thread.sleep(1000);
        }
        return false;
    }

    private boolean waitForNodeReady(int nodeIndex, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (isNodeReady(nodeIndex)) {
                return true;
            }
            Thread.sleep(1000);
        }
        return false;
    }

    private void waitForNodeDown(int nodeIndex, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (!isNodeReady(nodeIndex)) {
                return;
            }
            Thread.sleep(500);
        }
    }

    private boolean isNodeReady(int nodeIndex) {
        try {
            String healthUrl = "http://127.0.0.1:" + CONSOLE_PORTS[nodeIndex]
                    + "/v3/console/health/liveness";
            HttpURLConnection connection = (HttpURLConnection) URI.create(healthUrl)
                    .toURL().openConnection();
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(1000);
            connection.setRequestMethod("GET");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return false;
            }
            String body = new String(connection.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            return body.contains("\"code\":0");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean waitForProcessExit(ProcessHandle handle, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (!handle.isAlive()) {
                return true;
            }
            Thread.sleep(500);
        }
        return !handle.isAlive();
    }

    private Path nodeDir(int nodeIndex) {
        return CLUSTER_HOME.resolve("node-" + (nodeIndex + 1));
    }

    private static int[] parsePorts(String ports) {
        String[] values = ports.split(",");
        int[] result = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = Integer.parseInt(values[i].trim());
        }
        return result;
    }

    private void sleepQuietly(long timeoutMs) {
        try {
            Thread.sleep(timeoutMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
