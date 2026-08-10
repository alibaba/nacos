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
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Coordinates the temporary rolling-upgrade compatibility window for JRaft gRPC authentication.
 *
 * <p>This component is deliberately isolated from the permanent credential transport and validation
 * code. It allows requests with invalid credentials only until every member reports JRaft auth
 * support, persists the irreversible enforcement decision, and restores that decision after a
 * restart. It will be removed in Nacos 4.0.0, when JRaft authentication is always enforced.</p>
 *
 * @author xiweng.yy
 * @deprecated only used for a smooth rolling upgrade; it will be removed in Nacos 4.0.0
 */
@Component
@Deprecated(since = "3.2.4", forRemoval = true)
public class JRaftAuthUpgradeCoordinator {
    
    static final String STATE_FILE_NAME = "jraft-auth-enforced.state";
    
    private static final String STATE_FILE_VERSION = "version=1";
    
    private static final String ENFORCED_STATE = "state=ENFORCED";
    
    private static final long WARNING_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(1);
    
    private final ServerMemberManager serverMemberManager;
    
    private final Path stateFile;
    
    private final AtomicBoolean enforced = new AtomicBoolean(false);
    
    private final AtomicBoolean statePersisted = new AtomicBoolean(false);
    
    private final AtomicLong nextWarningTime = new AtomicLong(0L);
    
    @Autowired
    public JRaftAuthUpgradeCoordinator(ServerMemberManager serverMemberManager) {
        this(serverMemberManager,
            Path.of(EnvUtil.getNacosHome(), "data", STATE_FILE_NAME));
    }
    
    JRaftAuthUpgradeCoordinator(ServerMemberManager serverMemberManager, Path stateFile) {
        this.serverMemberManager = serverMemberManager;
        this.stateFile = stateFile;
        loadState();
    }
    
    /**
     * Returns whether invalid JRaft credentials must be rejected.
     *
     * @return {@code true} if authentication enforcement has been latched
     */
    public boolean isEnforced() {
        return enforced.get();
    }
    
    /**
     * Handles an invalid credential according to the temporary rolling-upgrade state.
     *
     * @return {@code true} only while the compatibility window still allows the request
     */
    public boolean allowInvalidCredential() {
        if (enforced.get()) {
            return false;
        }
        warnInvalidCredential();
        return true;
    }
    
    /**
     * Checks the complete member view and irreversibly enables JRaft authentication when every
     * member reports support. In-memory enforcement is enabled immediately, while state-file
     * persistence is retried independently until it succeeds.
     */
    @Scheduled(fixedRate = 3000)
    public synchronized void doCheck() {
        if (enforced.get()) {
            persistStateIfNecessary();
            return;
        }
        Collection<Member> members = serverMemberManager.allMembers();
        if (members == null || members.isEmpty()) {
            return;
        }
        for (Member each : members) {
            if (!Boolean.TRUE.equals(
                each.getExtendVal(MemberMetaDataConstants.SUPPORT_JRAFT_AUTH))) {
                return;
            }
        }
        enforced.set(true);
        Loggers.RAFT.info(
            "All Nacos servers support JRaft authentication; enforcement is enabled");
        persistStateIfNecessary();
    }
    
    private void loadState() {
        if (!Files.isRegularFile(stateFile)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(stateFile, StandardCharsets.UTF_8);
            if (lines.contains(STATE_FILE_VERSION) && lines.contains(ENFORCED_STATE)) {
                enforced.set(true);
                statePersisted.set(true);
                Loggers.RAFT.info(
                    "Restored enforced JRaft authentication state from persistent marker");
            } else {
                Loggers.RAFT.warn("Ignored invalid JRaft authentication state file at {}",
                    stateFile);
            }
        } catch (IOException e) {
            Loggers.RAFT.warn("Failed to read JRaft authentication state file at {}", stateFile,
                e);
        }
    }
    
    private void persistStateIfNecessary() {
        if (statePersisted.get()) {
            return;
        }
        try {
            persistState();
            statePersisted.set(true);
        } catch (IOException e) {
            Loggers.RAFT.error(
                "Failed to persist JRaft authentication enforcement state; will retry", e);
        }
    }
    
    private void persistState() throws IOException {
        Path parent = stateFile.getParent();
        Files.createDirectories(parent);
        Path temporaryFile = parent.resolve(STATE_FILE_NAME + ".tmp");
        Files.write(temporaryFile, Arrays.asList(STATE_FILE_VERSION, ENFORCED_STATE),
            StandardCharsets.UTF_8, StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            Files.move(temporaryFile, stateFile, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporaryFile, stateFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }
    
    private void warnInvalidCredential() {
        long now = System.currentTimeMillis();
        long next = nextWarningTime.get();
        if (now >= next && nextWarningTime.compareAndSet(next,
            now + WARNING_INTERVAL_MILLIS)) {
            Loggers.RAFT.warn(
                "Allowed a JRaft request with missing or invalid server identity during "
                    + "the rolling-upgrade compatibility window");
        }
    }
}
