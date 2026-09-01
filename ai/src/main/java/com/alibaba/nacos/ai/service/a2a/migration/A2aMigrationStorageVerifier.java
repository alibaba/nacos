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

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.agent.AgentVersionContent;
import com.alibaba.nacos.ai.model.agent.AgentVersionStorageDescriptor;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionContentSerializer;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionStorageService;
import com.alibaba.nacos.ai.service.agent.storage.PreparedAgentVersionWrite;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.plugin.ai.storage.model.AiResourceStorageChangeEvent;
import com.alibaba.nacos.plugin.ai.storage.model.AiResourceStorageConsistencyMode;
import com.alibaba.nacos.plugin.ai.storage.spi.AiResourceStorageChangeListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Temporary compatibility support for migrating Nacos 3.0-3.2 A2A data.
 *
 * <p>TODO(remove in 4.0): remove after the historical A2A migration window closes.</p>
 *
 * @author Nacos
 */
@Component
public class A2aMigrationStorageVerifier {
    
    private static final int DEFAULT_MAX_READ_ATTEMPTS = 4;
    
    private static final long DEFAULT_RETRY_DELAY_MILLIS = 100L;
    
    private final AgentVersionStorageService storageService;
    
    private final int maxReadAttempts;
    
    private final RetryWaiter retryWaiter;
    
    @Autowired
    public A2aMigrationStorageVerifier(AgentVersionStorageService storageService) {
        this(storageService, DEFAULT_MAX_READ_ATTEMPTS,
            new BlockingRetryWaiter(DEFAULT_RETRY_DELAY_MILLIS));
    }
    
    A2aMigrationStorageVerifier(AgentVersionStorageService storageService, int maxReadAttempts,
        RetryWaiter retryWaiter) {
        if (maxReadAttempts < 1) {
            throw new IllegalArgumentException("Storage read attempts must be positive");
        }
        this.storageService = storageService;
        this.maxReadAttempts = maxReadAttempts;
        this.retryWaiter = retryWaiter;
    }
    
    /**
     * Save one prepared Version and verify exact bytes, size, digest, and decoded content.
     *
     * @param prepared prepared standard Agent Version content
     * @throws NacosException when storage never becomes readable or content differs
     */
    public void saveAndVerify(PreparedAgentVersionWrite prepared) throws NacosException {
        if (prepared == null) {
            throw new IllegalArgumentException("Prepared Agent Version content must not be null");
        }
        AgentVersionStorageDescriptor descriptor = prepared.getDescriptor();
        AiResourceStorageConsistencyMode mode = storageService.consistencyMode(descriptor);
        AtomicBoolean visibilityHint = new AtomicBoolean(false);
        AiResourceStorageChangeListener listener = event -> onStorageChanged(event, descriptor,
            visibilityHint);
        boolean listening = AiResourceStorageConsistencyMode.EVENTUAL_WITH_NOTIFICATION == mode;
        if (listening) {
            storageService.addChangeListener(listener);
        }
        try {
            storageService.save(prepared);
            verifyWithRetry(prepared, mode, visibilityHint);
        } finally {
            if (listening) {
                storageService.removeChangeListener(listener);
            }
        }
    }
    
    private void verifyWithRetry(PreparedAgentVersionWrite prepared,
        AiResourceStorageConsistencyMode mode, AtomicBoolean visibilityHint)
        throws NacosException {
        for (int attempt = 1; attempt < maxReadAttempts; attempt++) {
            try {
                verifyOnce(prepared);
                return;
            } catch (NacosException e) {
                if (AiResourceStorageConsistencyMode.STRONG == mode) {
                    throw e;
                }
            }
            retryWaiter.await(mode, visibilityHint);
        }
        verifyOnce(prepared);
    }
    
    private void verifyOnce(PreparedAgentVersionWrite prepared) throws NacosException {
        AgentVersionContent loaded = storageService.load(prepared.getDescriptor());
        byte[] actual = AgentVersionContentSerializer.serialize(loaded).getBytes();
        if (!Arrays.equals(prepared.getBytes(), actual)) {
            throw new NacosException(NacosException.SERVER_ERROR,
                "Migrated Agent Version content differs after storage read-back");
        }
    }
    
    private void onStorageChanged(AiResourceStorageChangeEvent event,
        AgentVersionStorageDescriptor descriptor, AtomicBoolean visibilityHint) {
        if (event != null && descriptor.getProvider().equals(event.getProvider())
            && Constants.Agent.RESOURCE_TYPE_AGENT.equals(event.getResourceType())) {
            visibilityHint.set(true);
        }
    }
    
    interface RetryWaiter {
        
        void await(AiResourceStorageConsistencyMode mode, AtomicBoolean visibilityHint)
            throws NacosException;
    }
    
    private static final class BlockingRetryWaiter implements RetryWaiter {
        
        private final long delayMillis;
        
        private BlockingRetryWaiter(long delayMillis) {
            this.delayMillis = delayMillis;
        }
        
        @Override
        public void await(AiResourceStorageConsistencyMode mode,
            AtomicBoolean visibilityHint) throws NacosException {
            if (AiResourceStorageConsistencyMode.EVENTUAL_WITH_NOTIFICATION == mode
                && visibilityHint.getAndSet(false)) {
                return;
            }
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NacosException(NacosException.SERVER_ERROR,
                    "Interrupted while verifying migrated Agent Version storage", e);
            }
        }
    }
}
