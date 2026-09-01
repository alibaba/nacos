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

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * One applied current-fact projection update.
 *
 * @author Nacos
 */
public final class AgentProjectionUpdate {
    
    private final AgentProjectionKey key;
    
    private final AgentProjectionState previous;
    
    private final AgentProjectionState current;
    
    private final Set<AgentProjectionChangeReason> reasons;
    
    AgentProjectionUpdate(AgentProjectionKey key, AgentProjectionState previous,
        AgentProjectionState current, Set<AgentProjectionChangeReason> reasons) {
        this.key = key;
        this.previous = previous;
        this.current = current;
        this.reasons = reasons.isEmpty()
            ? Collections.<AgentProjectionChangeReason>emptySet()
            : Collections.unmodifiableSet(EnumSet.copyOf(reasons));
    }
    
    public AgentProjectionKey getKey() {
        return key;
    }
    
    public AgentProjectionState getPrevious() {
        return previous;
    }
    
    public AgentProjectionState getCurrent() {
        return current;
    }
    
    public Set<AgentProjectionChangeReason> getReasons() {
        return reasons;
    }
    
    public boolean isPublicObservationChanged() {
        return !current.samePublicObservation(previous);
    }
}
