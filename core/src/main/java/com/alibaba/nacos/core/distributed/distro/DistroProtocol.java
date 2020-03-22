/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.distributed.distro;

import com.alibaba.nacos.consistency.Log;
import com.alibaba.nacos.consistency.LogFuture;
import com.alibaba.nacos.consistency.LogProcessor;
import com.alibaba.nacos.consistency.ap.APProtocol;
import com.alibaba.nacos.consistency.ap.LogProcessor4AP;
import com.alibaba.nacos.consistency.exception.NoSuchLogProcessorException;
import com.alibaba.nacos.consistency.request.GetRequest;
import com.alibaba.nacos.consistency.request.GetResponse;
import com.alibaba.nacos.consistency.store.KVStore;
import com.alibaba.nacos.core.cluster.MemberManager;
import com.alibaba.nacos.core.distributed.AbstractConsistencyProtocol;
import com.alibaba.nacos.core.distributed.distro.core.DistroServer;
import com.alibaba.nacos.core.distributed.distro.utils.DistroExecutor;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class DistroProtocol extends AbstractConsistencyProtocol<DistroConfig, LogProcessor4AP> implements APProtocol<DistroConfig> {

    private final AtomicBoolean initialize = new AtomicBoolean(false);
    private KVManager kvManager;
    private DistroServer distroServer;
    private MemberManager memberManager;
    private DistroConfig distroConfig;

    public DistroProtocol(MemberManager memberManager) {
        this.memberManager = memberManager;
        this.kvManager = new KVManager();
    }

    @Override
    public void init(DistroConfig config) {
        if (initialize.compareAndSet(false, true)) {
            this.distroConfig = config;
            this.distroServer = new DistroServer(kvManager, this.distroConfig);

            loadLogProcessor(config.listLogProcessor());

            // distro server start
            this.distroServer.start();
        }
    }

    @Override
    public <D> GetResponse<D> getData(GetRequest request) throws Exception {
        final String group = request.getGroup();
        LogProcessor processor = allProcessor().get(group);
        if (processor != null) {
            return processor.getData(request);
        }
        throw new NoSuchLogProcessorException(group);
    }

    @Override
    public LogFuture submit(Log data) throws Exception {
        final String group = data.getGroup();
        LogProcessor processor = allProcessor().get(group);
        if (processor != null) {
            processor.onApply(data);
            return distroServer.submit(data);
        }
        throw new NoSuchLogProcessorException(group);
    }

    @Override
    public CompletableFuture<LogFuture> submitAsync(Log data) {
        final CompletableFuture<LogFuture> future = new CompletableFuture<>();
        DistroExecutor.executeByGlobal(() -> {
            try {
                future.complete(submit(data));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @Override
    public void addMembers(Set<String> addresses) {
        distroConfig.addMembers(addresses);
    }

    @Override
    public void removeMembers(Set<String> addresses) {
        distroConfig.removeMembers(addresses);
    }

    @Override
    public void shutdown() {
        if (distroServer != null) {
            distroServer.shutdown();
        }
    }

    @Override
    public KVStore<CheckSum> createKVStore(String storeName) {
        DistroKVStore kvStore = new DistroKVStore(storeName);
        this.kvManager.addKVStore(kvStore);

        DistroExecutor.executeByGlobal(() -> {
            // Because Distro uses DistroProtocol internally, so LogProcessor is implemented, need to add
            LogProcessor4AP processor = kvStore.getKVLogProcessor();
            processor.injectProtocol(this);
            loadLogProcessor(Collections.singletonList(processor));
        });

        return kvStore;
    }

    public DistroServer getDistroServer() {
        return distroServer;
    }
}
