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

package com.alibaba.nacos.test.sdk.lock;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.lock.LockService;
import com.alibaba.nacos.api.lock.common.LockConstants;
import com.alibaba.nacos.api.lock.model.LockInstance;
import com.alibaba.nacos.test.sdk.JavaSdkBaseITCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for Java SDK {@link LockService}.
 *
 * <p>The full scenario matrix and remaining gaps are recorded in
 * {@code test/java-sdk-test/JAVA_SDK_IT_SCENARIOS.md}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: acquire and release a distributed Nacos mutex lock through the
 *     public Java SDK factory and through direct remote methods.</li>
 *     <li>Boundary/validation: a second SDK client cannot acquire the same held lock, release
 *     clears the lock, released or expired keys can be acquired again, repeated release returns
 *     false, and null lock instances fail at the SDK boundary.</li>
 *     <li>Error handling: unsupported lock type is returned as a controlled
 *     {@link NacosException} instead of an uncontrolled client runtime failure; missing lock key
 *     is also mapped to a controlled server error.</li>
 *     <li>Directed recovery: a real standalone replacement clears a long connection-scoped lease;
 *     both original clients reconnect and preserve mutex compete, release, and reacquire
 *     behavior.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class LockServiceJavaSdkITCase extends JavaSdkBaseITCase {
    
    private static final long EXPIRING_LOCK_LEASE_MILLIS = 5000L;

    private static final long RESTART_LOCK_LEASE_MILLIS = 180000L;

    private static final long RECONNECT_TIMEOUT_MILLIS = 120000L;

    private static final String RECONNECT_ENABLED_PROPERTY = "nacos.lock.reconnect.enabled";

    private static final String RECONNECT_CONTROL_DIR_PROPERTY =
            "nacos.lock.reconnect.control.dir";

    @Test
    public void testAcquireCompeteReleaseAndReacquireLock() throws Exception {
        LockService owner = createLockService();
        LockService contender = createLockService();
        LockInstance lock = newLockInstance("lifecycle", LockConstants.NACOS_LOCK_TYPE);
        addCleanup(() -> owner.unLock(lock));
        addCleanup(() -> contender.unLock(lock));
        
        assertTrue(owner.lock(lock));
        assertFalse(contender.lock(lock));
        assertTrue(owner.unLock(lock));
        assertTrue(contender.lock(lock));
        assertTrue(contender.unLock(lock));
        assertFalse(owner.unLock(lock));
    }
    
    @Test
    public void testDirectRemoteTryLockAndReleaseLock() throws Exception {
        LockService lockService = createLockService();
        LockInstance lock = newLockInstance("remote", LockConstants.NACOS_LOCK_TYPE);
        addCleanup(() -> lockService.remoteReleaseLock(lock));
        
        assertTrue(lockService.remoteTryLock(lock));
        assertFalse(lockService.remoteTryLock(lock));
        assertTrue(lockService.remoteReleaseLock(lock));
        assertFalse(lockService.remoteReleaseLock(lock));
    }
    
    @Test
    public void testExpiredLockCanBeAcquiredByAnotherClient() throws Exception {
        LockService owner = createLockService();
        LockService contender = createLockService();
        LockInstance lock = new LockInstance("java-sdk-it-lock-expire-"
                + randomServiceName("lock"), EXPIRING_LOCK_LEASE_MILLIS,
                LockConstants.NACOS_LOCK_TYPE);
        addCleanup(() -> owner.unLock(lock));
        addCleanup(() -> contender.unLock(lock));
        
        assertTrue(owner.lock(lock));
        assertFalse(contender.lock(lock));
        waitUntil("expired lock should be acquirable by another client",
                () -> contender.lock(lock));
        assertTrue(contender.unLock(lock));
    }

    @Test
    @EnabledIfSystemProperty(named = RECONNECT_ENABLED_PROPERTY, matches = "true")
    public void shouldReconnectOriginalClientsAndResetConnectionScopedLockAfterRealServerRestart()
            throws Exception {
        Path controlDirectory = reconnectControlDirectory();
        Path ready = resetMarker(controlDirectory, "client-ready");
        Path serverStopped = resetMarker(controlDirectory, "server-stopped");
        Path downObserved = resetMarker(controlDirectory, "client-observed-down");
        Path serverRestarted = resetMarker(controlDirectory, "server-restarted");

        LockService owner = createLockService();
        LockService contender = createLockService();
        LockInstance held = new LockInstance("java-sdk-it-lock-restart-"
                + randomServiceName("lock"), RESTART_LOCK_LEASE_MILLIS,
                LockConstants.NACOS_LOCK_TYPE);
        LockInstance downProbe = newLockInstance("restart-down-probe",
                LockConstants.NACOS_LOCK_TYPE);
        addCleanup(() -> owner.remoteReleaseLock(downProbe));
        addCleanup(() -> contender.remoteReleaseLock(held));
        addCleanup(() -> owner.remoteReleaseLock(held));

        assertTrue(owner.remoteTryLock(held));
        assertFalse(contender.remoteTryLock(held));
        writeMarker(ready, held.getKey());

        waitForMarker(serverStopped, "external harness should stop the standalone server");
        waitUntil("a remote lock operation should observe the stopped server",
                RECONNECT_TIMEOUT_MILLIS, () -> remoteLockUnavailable(owner, downProbe));
        writeMarker(downObserved, held.getKey());
        waitForMarker(serverRestarted, "external harness should restart the standalone server");

        waitUntil("the original contender should reconnect and acquire the reset lock",
                RECONNECT_TIMEOUT_MILLIS, () -> remoteTryLock(contender, held));
        assertFalse(owner.remoteTryLock(held),
                "the other original client must still observe the restored mutex");
        assertTrue(contender.remoteReleaseLock(held));
        assertTrue(owner.remoteTryLock(held));
        assertTrue(owner.remoteReleaseLock(held));
    }
    
    @Test
    public void testInvalidLockInputThrowsControlledException() throws Exception {
        LockService lockService = createLockService();
        LockInstance unsupported = newLockInstance("unsupported-type", "UNKNOWN_LOCK_TYPE");
        LockInstance missingKey = new LockInstance(null, 30000L, LockConstants.NACOS_LOCK_TYPE);
        
        assertThrows(NacosException.class, () -> lockService.lock(unsupported));
        assertThrows(NacosException.class, () -> lockService.lock(missingKey));
        assertThrows(NullPointerException.class, () -> lockService.lock(null));
    }
    
    private LockInstance newLockInstance(String scenario, String lockType) {
        return new LockInstance("java-sdk-it-lock-" + scenario + "-" + randomServiceName("lock"),
                30000L, lockType);
    }

    private boolean remoteLockUnavailable(LockService service, LockInstance lock) {
        try {
            service.remoteTryLock(lock);
            return false;
        } catch (NacosException expected) {
            return true;
        }
    }

    private boolean remoteTryLock(LockService service, LockInstance lock) {
        try {
            return service.remoteTryLock(lock);
        } catch (NacosException transientFailure) {
            return false;
        }
    }

    private Path reconnectControlDirectory() throws Exception {
        String value = System.getProperty(RECONNECT_CONTROL_DIR_PROPERTY, "");
        if (value.isBlank()) {
            throw new IllegalStateException("Missing required restart IT property: "
                    + RECONNECT_CONTROL_DIR_PROPERTY);
        }
        Path result = Paths.get(value);
        Files.createDirectories(result);
        return result;
    }

    private Path resetMarker(Path controlDirectory, String name) throws Exception {
        Path result = controlDirectory.resolve(name);
        Files.deleteIfExists(result);
        return result;
    }

    private void waitForMarker(Path marker, String reason) throws Exception {
        waitUntil(reason, RECONNECT_TIMEOUT_MILLIS, () -> Files.isRegularFile(marker));
    }

    private void writeMarker(Path marker, String value) throws Exception {
        Files.write(marker, Collections.singletonList(value), StandardCharsets.UTF_8);
    }
}
