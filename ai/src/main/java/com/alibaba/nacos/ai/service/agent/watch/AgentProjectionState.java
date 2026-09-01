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

package com.alibaba.nacos.ai.service.agent.watch;

import com.alibaba.nacos.naming.core.v2.pojo.Service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable current state of one Agent projection without its business snapshot.
 *
 * @author Nacos
 */
public final class AgentProjectionState {
    
    private final AgentProjectionStatus status;
    
    private final String fingerprint;
    
    private final Set<Service> physicalDependencies;
    
    private final Integer errorCode;
    
    private final String errorMessage;
    
    private final long computedAt;
    
    private AgentProjectionState(AgentProjectionStatus status, String fingerprint,
        Set<Service> physicalDependencies, Integer errorCode, String errorMessage,
        long computedAt) {
        this.status = status;
        this.fingerprint = fingerprint;
        this.physicalDependencies = immutableDependencies(physicalDependencies);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.computedAt = computedAt;
    }
    
    static AgentProjectionState available(String fingerprint, Set<Service> dependencies,
        long computedAt) {
        return new AgentProjectionState(AgentProjectionStatus.AVAILABLE, fingerprint,
            dependencies, null, null, computedAt);
    }
    
    static AgentProjectionState failure(AgentProjectionStatus status, int errorCode,
        String errorMessage, long computedAt) {
        return new AgentProjectionState(status, null, Collections.<Service>emptySet(), errorCode,
            errorMessage, computedAt);
    }
    
    AgentProjectionState withPhysicalDependencies(Set<Service> dependencies) {
        return new AgentProjectionState(status, fingerprint, dependencies, errorCode,
            errorMessage, computedAt);
    }
    
    public AgentProjectionStatus getStatus() {
        return status;
    }
    
    public String getFingerprint() {
        return fingerprint;
    }
    
    public Set<Service> getPhysicalDependencies() {
        return physicalDependencies;
    }
    
    public Integer getErrorCode() {
        return errorCode;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public long getComputedAt() {
        return computedAt;
    }
    
    public boolean isAvailable() {
        return status == AgentProjectionStatus.AVAILABLE;
    }
    
    boolean replacesPhysicalDependencies() {
        return status == AgentProjectionStatus.AVAILABLE
            || status == AgentProjectionStatus.NOT_FOUND;
    }
    
    boolean requiresRetry() {
        return status == AgentProjectionStatus.TRANSIENT_FAILURE;
    }
    
    boolean samePublicObservation(AgentProjectionState other) {
        return other != null && status == other.status
            && Objects.equals(fingerprint, other.fingerprint)
            && Objects.equals(errorCode, other.errorCode);
    }
    
    private static Set<Service> immutableDependencies(Set<Service> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<Service>(source));
    }
}
