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
import com.alibaba.nacos.consistency.cp.APProtocol;
import com.alibaba.nacos.consistency.request.GetRequest;
import com.alibaba.nacos.core.distributed.AbstractConsistencyProtocol;
import com.alibaba.nacos.core.executor.ExecutorFactory;
import com.alibaba.nacos.core.utils.SpringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * // TODO 将 naming 模块的 AP 协议下沉到 core 模块
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class DistroProtocol extends AbstractConsistencyProtocol<DistroConfig> implements APProtocol<DistroConfig> {

    private final Map<String, Object> metaData = new ConcurrentHashMap<>();

    private final Executor executor = ExecutorFactory.newFixExecutorService(DistroProtocol.class.getCanonicalName(), 4);

    private List<AbstractDistroKVStore> distroStores;

    private DistroServer distroServer;

    @Override
    public void init(DistroConfig config) {
        this.distroStores = new ArrayList<>(SpringUtils.getBeansOfType(AbstractDistroKVStore.class).values());
        this.distroServer = new DistroServer(distroStores);

        loadLogDispatcher(config.listLogProcessor());
    }

    @Override
    public Map<String, Object> protocolMetaData() {
        return new HashMap<>(metaData);
    }

    @Override
    public <R> R metaData(String key, String... subKey) {
        Object o = metaData.get(key);
        if (subKey == null || subKey.length == 0) {
            return (R) o;
        }
        return (R) getVIfMapByRecursive(o, 0, subKey);
    }

    @Override
    public <D> D getData(String key) throws Exception {
        for (Map.Entry<String, LogProcessor> entry : allProcessor().entrySet()) {
            final LogProcessor processor = entry.getValue();
            if (processor.interest(key)) {
                return processor.getData(new GetRequest(key));
            }
        }
        return null;
    }

    @Override
    public boolean submit(Log data) throws Exception {
        final String key = data.getKey();
        for (Map.Entry<String, LogProcessor> entry : allProcessor().entrySet()) {
            final LogProcessor processor = entry.getValue();
            if (processor.interest(key)) {
                return processor.onApply(data);
            }
        }
        return false;
    }

    @Override
    public CompletableFuture<Boolean> submitAsync(Log data) {
        final CompletableFuture<Boolean> future = new CompletableFuture<>();
        executor.execute(() -> {
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
            executor.execute(() -> {
                for (Log log : entry.getValue()) {
                    try {
                        processor.onApply(log);
                    } catch (Exception e) {
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

}
