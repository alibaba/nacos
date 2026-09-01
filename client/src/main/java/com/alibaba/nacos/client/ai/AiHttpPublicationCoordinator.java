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

package com.alibaba.nacos.client.ai;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.agent.ClientLivenessInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.lifecycle.Closeable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Schedules one heartbeat and coordinated redo cycle for all AI HTTP publishers in an
 * {@code AiService}.
 *
 * <p>When the server reports a missing HTTP Client, every participant is marked dirty before any
 * redo begins. This prevents an Agent or MCP replay from recreating the shared Client and hiding
 * publication loss in the other module.</p>
 *
 * @author Nacos
 */
class AiHttpPublicationCoordinator implements Closeable {
    
    private static final Logger LOGGER = LogUtils.logger(AiHttpPublicationCoordinator.class);
    
    private final ScheduledExecutorService executor;
    
    private final Map<AiHttpPublicationParticipant, Boolean> participantActivity =
        new LinkedHashMap<AiHttpPublicationParticipant, Boolean>();
    
    private long heartbeatIntervalMillis = AiConstants.DEFAULT_AI_CACHE_UPDATE_INTERVAL;
    
    private ScheduledFuture<?> maintenanceFuture;
    
    private boolean maintaining;
    
    private boolean closed;
    
    AiHttpPublicationCoordinator() {
        this(new ScheduledThreadPoolExecutor(1,
            new NameThreadFactory("com.alibaba.nacos.client.ai.http.publication")));
    }
    
    AiHttpPublicationCoordinator(ScheduledExecutorService executor) {
        this.executor = executor;
    }
    
    synchronized void register(AiHttpPublicationParticipant participant) {
        participantActivity.put(participant, false);
    }
    
    synchronized void stateChanged(AiHttpPublicationParticipant participant,
        ClientLivenessInfo liveness, boolean hasHttpPublication) {
        if (closed) {
            return;
        }
        participantActivity.put(participant, hasHttpPublication);
        if (liveness != null && liveness.getHeartbeatIntervalMillis() > 0) {
            heartbeatIntervalMillis = liveness.getHeartbeatIntervalMillis();
        }
        if (maintaining) {
            return;
        }
        scheduleIfRequired();
    }
    
    private void scheduleIfRequired() {
        if (closed || !hasHttpPublication()) {
            cancelMaintenance();
            return;
        }
        if (maintenanceFuture == null || maintenanceFuture.isDone()) {
            maintenanceFuture = executor.schedule(new Runnable() {
                
                @Override
                public void run() {
                    maintain();
                }
            }, heartbeatIntervalMillis, TimeUnit.MILLISECONDS);
        }
    }
    
    private void maintain() {
        List<AiHttpPublicationParticipant> snapshot;
        synchronized (this) {
            maintenanceFuture = null;
            if (closed || !hasHttpPublication()) {
                return;
            }
            maintaining = true;
            snapshot = new ArrayList<AiHttpPublicationParticipant>(
                participantActivity.keySet());
        }
        try {
            redoAll(snapshot);
            AiHttpPublicationParticipant heartbeatOwner = firstRegistered(snapshot);
            if (heartbeatOwner != null) {
                try {
                    ClientLivenessInfo liveness = heartbeatOwner.heartbeat();
                    stateChanged(heartbeatOwner, liveness,
                        heartbeatOwner.hasHttpPublication());
                } catch (NacosException e) {
                    if (e.getErrCode() == ErrorCode.HTTP_CLIENT_NOT_FOUND.getCode()) {
                        markAllDirty(snapshot);
                        redoAll(snapshot);
                    } else {
                        LOGGER.warn("Shared AI HTTP publication heartbeat through {} failed.",
                            heartbeatOwner.getPublicationModuleName(), e);
                    }
                }
            }
        } finally {
            synchronized (this) {
                maintaining = false;
                scheduleIfRequired();
            }
        }
    }
    
    private void markAllDirty(List<AiHttpPublicationParticipant> snapshot) {
        for (AiHttpPublicationParticipant participant : snapshot) {
            participant.markHttpPublicationsDirty();
        }
    }
    
    private void redoAll(List<AiHttpPublicationParticipant> snapshot) {
        for (AiHttpPublicationParticipant participant : snapshot) {
            participant.redoDirtyHttpPublications();
        }
    }
    
    private AiHttpPublicationParticipant firstRegistered(
        List<AiHttpPublicationParticipant> snapshot) {
        for (AiHttpPublicationParticipant participant : snapshot) {
            if (participant.hasRegisteredHttpPublication()) {
                return participant;
            }
        }
        return null;
    }
    
    private boolean hasHttpPublication() {
        for (Boolean active : participantActivity.values()) {
            if (active) {
                return true;
            }
        }
        return false;
    }
    
    private void cancelMaintenance() {
        if (maintenanceFuture != null) {
            maintenanceFuture.cancel(false);
            maintenanceFuture = null;
        }
    }
    
    @Override
    public synchronized void shutdown() {
        if (closed) {
            return;
        }
        closed = true;
        cancelMaintenance();
        participantActivity.clear();
        executor.shutdownNow();
    }
}
