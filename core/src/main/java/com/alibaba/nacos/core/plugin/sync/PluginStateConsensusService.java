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

package com.alibaba.nacos.core.plugin.sync;

import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.core.plugin.PluginStateProcessor;
import com.alibaba.nacos.core.plugin.condition.ConditionOnClusterMode;
import com.alibaba.nacos.core.utils.ClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Isolated lifecycle for the optional {@code plugin_state} CP group.
 *
 * @author Nacos
 */
@Component
@Lazy
@Conditional(ConditionOnClusterMode.class)
public class PluginStateConsensusService {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(PluginStateConsensusService.class);
    
    private static final ExecutorService REGISTRATION_EXECUTOR =
        ExecutorFactory.Managed.newSingleExecutorService(
            ClassUtils.getCanonicalName(PluginStateConsensusService.class),
            new NameThreadFactory("nacos-plugin-consensus"));
    
    private final PluginStateProcessor processor;
    
    private final ProtocolManager protocolManager;
    
    private final Executor executor;
    
    private final AtomicReference<RegistrationState> state =
        new AtomicReference<>(RegistrationState.NEW);
    
    private volatile CPProtocol protocol;
    
    private volatile Throwable failure;
    
    @Autowired
    public PluginStateConsensusService(PluginStateProcessor processor,
        ProtocolManager protocolManager) {
        this(processor, protocolManager, REGISTRATION_EXECUTOR);
    }
    
    PluginStateConsensusService(PluginStateProcessor processor,
        ProtocolManager protocolManager, Executor executor) {
        this.processor = processor;
        this.protocolManager = protocolManager;
        this.executor = executor;
    }
    
    /**
     * Start group registration without blocking Nacos startup.
     */
    public void initialize() {
        if (!state.compareAndSet(RegistrationState.NEW, RegistrationState.INITIALIZING)) {
            return;
        }
        try {
            executor.execute(this::register);
        } catch (RuntimeException e) {
            markUnavailable(e);
        }
    }
    
    /**
     * Get the successfully registered protocol.
     *
     * @return CP protocol
     * @throws IllegalStateException when registration has not succeeded
     */
    public CPProtocol getProtocol() {
        if (!isAvailable()) {
            throw new IllegalStateException("Plugin consensus group is unavailable, state="
                + state.get() + getFailureSuffix());
        }
        return protocol;
    }
    
    /**
     * Whether the plugin consensus group is registered.
     *
     * @return true when cluster plugin writes can be submitted
     */
    public boolean isAvailable() {
        return RegistrationState.AVAILABLE == state.get();
    }
    
    RegistrationState getState() {
        return state.get();
    }
    
    private void register() {
        try {
            CPProtocol currentProtocol = protocolManager.getCpProtocol();
            if (currentProtocol == null) {
                throw new IllegalStateException("CP protocol is not available");
            }
            currentProtocol.addRequestProcessors(Collections.singletonList(processor));
            protocol = currentProtocol;
            state.set(RegistrationState.AVAILABLE);
            LOGGER.info("[PluginStateConsensusService] Registered plugin_state CP group.");
        } catch (RuntimeException | LinkageError e) {
            markUnavailable(e);
        }
    }
    
    private void markUnavailable(Throwable cause) {
        failure = cause;
        protocol = null;
        state.set(RegistrationState.UNAVAILABLE);
        LOGGER.error("[PluginStateConsensusService] Failed to register plugin_state CP group. "
            + "Nacos startup continues with the accepted local plugin state and config view; "
            + "cluster plugin writes are unavailable.", cause);
    }
    
    private String getFailureSuffix() {
        Throwable currentFailure = failure;
        if (currentFailure == null) {
            return "";
        }
        String message = currentFailure.getMessage();
        return ", cause=" + (message == null ? currentFailure.getClass().getName() : message);
    }
    
    enum RegistrationState {
        
        NEW,
        
        INITIALIZING,
        
        AVAILABLE,
        
        UNAVAILABLE
    }
}
