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

package com.alibaba.nacos.client.ai.watch;

/**
 * Signals from a Wire Watch transport to the transport-neutral Watch manager.
 *
 * @author Nacos
 */
public interface AgentWatchTransportCallback {
    
    /**
     * Invalidate the local complete result.
     *
     * <p>A {@code true} return means the signal is either already known equal or is durably
     * represented by current/backup refresh scheduling. Wire transports may acknowledge only
     * after this method returns {@code true}.</p>
     *
     * @param observedFingerprint optional server-observed fingerprint
     * @param forceRefresh whether equality must be ignored and Discover must run
     * @return whether the signal was accepted
     */
    boolean invalidate(String observedFingerprint, boolean forceRefresh);
    
    /**
     * Report a Watch target or eligibility failure.
     *
     * @param errorCode Nacos error code
     * @param errorMessage error description
     * @param terminal whether the local intent must be removed
     */
    void unavailable(int errorCode, String errorMessage, boolean terminal);
}
