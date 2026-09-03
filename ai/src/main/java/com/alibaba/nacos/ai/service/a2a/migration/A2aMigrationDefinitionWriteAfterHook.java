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

package com.alibaba.nacos.ai.service.a2a.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Temporary successful-write hook for Nacos 3.0-3.2 A2A definitions.
 *
 * <p>TODO(remove in 4.0): remove after the historical A2A migration window closes.</p>
 *
 * @author Nacos
 */
@Component
public class A2aMigrationDefinitionWriteAfterHook {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(A2aMigrationDefinitionWriteAfterHook.class);
    
    private final A2aMigrationDefinitionHintReconciler hintReconciler;
    
    public A2aMigrationDefinitionWriteAfterHook(
        A2aMigrationDefinitionHintReconciler hintReconciler) {
        this.hintReconciler = hintReconciler;
    }
    
    /**
     * Best-effort notify the migration reconciler after a historical mutation succeeds.
     *
     * @param namespaceId namespace identifier
     * @param agentName public Agent name
     */
    public void afterSuccessfulMutation(String namespaceId, String agentName) {
        try {
            if (!hintReconciler.submit(namespaceId, agentName)) {
                LOGGER.debug("Historical A2A write-after hint was not accepted: "
                    + "namespaceHash={}, agentHash={}", hash(namespaceId), hash(agentName));
            }
        } catch (RuntimeException e) {
            // The historical mutation is authoritative during SYNCING. Hint delivery can never
            // change the response already earned by that successful write.
            LOGGER.warn("Failed to submit historical A2A write-after hint: namespaceHash={}, "
                + "agentHash={}; periodic scan will repair", hash(namespaceId),
                hash(agentName), e);
        }
    }
    
    private static String hash(String value) {
        return value == null ? "null" : Integer.toHexString(value.hashCode());
    }
}
