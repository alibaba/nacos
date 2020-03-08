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

import com.alibaba.nacos.consistency.Config;
import com.alibaba.nacos.consistency.Log;
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
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.core.utils.SpringUtils;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

    @Override
    public void init(DistroConfig config) {
        if (initialize.compareAndSet(false, true)) {
            this.memberManager = SpringUtils.getBean(MemberManager.class);
            this.kvManager = new KVManager();
            this.distroServer = new DistroServer(memberManager, kvManager, config);

            loadLogDispatcher(config.listLogProcessor());

            // distro server start

            distroServer.start();
        }
    }

    @Override
    public <R> R metaData(String key, String... subKey) {
        return (R) metaData.get(key, subKey);
    }

    @Override
    public <D> GetResponse<D> getData(GetRequest request) throws Exception {
        final String biz = request.getBiz();
        LogProcessor processor = allProcessor().get(biz);
        if (processor != null) {
            return processor.getData(request);
        }
        throw new NoSuchLogProcessorException(biz);
    }

    @Override
    public boolean submit(Log data) throws Exception {
        final String biz = data.getBiz();
        LogProcessor processor = allProcessor().get(biz);
        if (processor != null) {
            processor.onApply(data);
            return distroServer.submit(data);
        }
        throw new NoSuchLogProcessorException(biz);
    }

    @Override
    public CompletableFuture<Boolean> submitAsync(Log data) {
        final CompletableFuture<Boolean> future = new CompletableFuture<>();
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
    public boolean batchSubmit(Map<String, List<Log>> datums) {
        for (Map.Entry<String, List<Log>> entry : datums.entrySet()) {
            final LogProcessor processor = allProcessor().get(entry.getKey());
            DistroExecutor.executeByGlobal(() -> {
                for (Log log : entry.getValue()) {
                    try {
                        processor.onApply(log);
                    } catch (Exception e) {
                        Loggers.DISTRO.error("An exception occurred while processing a transaction request, " +
                                "processor : {}, error : {}", processor, e);
                        processor.onError(e);
                    }
                }
            });
        }
        return true;
    }

    @Override
    public Class<? extends Config> configType() {
        return DistroConfig.class;
    }

    @Override
    public void shutdown() {
        distroServer.shutdown();
    }

    @Override
    public <D> KVStore<D> createKVStore(String storeName) {
        DistroKVStore<D> kvStore = new DistroKVStore<>(storeName);
        this.kvManager.addKVStore(kvStore);

        // Because Distro uses DistroProtocol internally, so LogProcessor is implemented, need to add

        LogProcessor4AP processor = kvStore.getKVLogProcessor();

        processor.injectProtocol(this);

        loadLogDispatcher(Collections.singletonList(processor));
        return kvStore;
    }

    public DistroServer getDistroServer() {
        return distroServer;
    }
}
