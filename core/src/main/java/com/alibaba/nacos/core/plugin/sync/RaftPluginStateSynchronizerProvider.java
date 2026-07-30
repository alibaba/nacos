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

import java.util.function.Supplier;

/**
 * Built-in Raft synchronizer provider.
 *
 * @author Nacos
 */
class RaftPluginStateSynchronizerProvider implements PluginStateSynchronizerProvider {
    
    static final String NAME = "raft";
    
    private final Supplier<PluginStateConsensusService> consensusServiceSupplier;
    
    RaftPluginStateSynchronizerProvider(
        Supplier<PluginStateConsensusService> consensusServiceSupplier) {
        this.consensusServiceSupplier = consensusServiceSupplier;
    }
    
    @Override
    public String getName() {
        return NAME;
    }
    
    @Override
    public PluginStateSynchronizer createSynchronizer(
        PluginStateSynchronizationContext context) {
        PluginStateConsensusService consensusService = consensusServiceSupplier.get();
        if (consensusService == null) {
            throw new IllegalStateException("Plugin state consensus service is unavailable");
        }
        return new RaftPluginStateSynchronizer(consensusService);
    }
}
