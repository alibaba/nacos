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

import com.alibaba.nacos.common.model.ResResult;
import com.alibaba.nacos.core.distributed.BizProcessor;
import com.alibaba.nacos.core.distributed.Config;
import com.alibaba.nacos.core.distributed.ConsistencyProtocol;
import com.alibaba.nacos.core.distributed.Log;
import com.alibaba.nacos.core.executor.ExecutorFactory;
import com.alibaba.nacos.core.utils.ResResultUtils;
import com.alibaba.nacos.core.utils.SpringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * // TODO 将 naming 模块的 AP 协议下沉到 core 模块
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
@Component(value = "DistroProtocol")
public class DistroProtocol implements ConsistencyProtocol<DistroConfig> {

    private final Map<String, BizProcessor> bizProcessorMap = new ConcurrentHashMap<>();

    private final Map<String, Object> metaData = new HashMap<>();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    private final Executor executor = ExecutorFactory.newFixExecutorService(DistroProtocol.class.getCanonicalName(), 4);

    private List<DistroStore> distroStores;

    private DistroServer distroServer;

    @Override
    public void init(DistroConfig config) {
        this.distroStores = new ArrayList<>(SpringUtils.getBeansOfType(DistroStore.class).values());

        this.distroServer = new DistroServer(distroStores);
    }

    @Override
    public Map<String, Object> protocolMetaData() {
        readLock.lock();
        try {
            return metaData;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public <T> T metaData(String key) {
        readLock.lock();
        try {
            return (T) metaData.get(key);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void registerBizProcessor(BizProcessor processor) {
        bizProcessorMap.put(processor.bizInfo(), processor);
    }

    @Override
    public <T> T getData(String key) throws Exception {
        for (Map.Entry<String, BizProcessor> entry : bizProcessorMap.entrySet()) {
            final BizProcessor processor = entry.getValue();
            if (processor.interest(key)) {
                return processor.getData(key);
            }
        }
        return null;
    }

    @Override
    public boolean submit(Log data) throws Exception {
        final String key = data.getKey();
        for (Map.Entry<String, BizProcessor> entry : bizProcessorMap.entrySet()) {
            final BizProcessor processor = entry.getValue();
            if (processor.interest(key)) {
                processor.onApply(data);
                return true;
            }
        }
        return false;
    }

    @Override
    public CompletableFuture<ResResult<Boolean>> submitAsync(Log data) {
        final CompletableFuture<ResResult<Boolean>> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                future.complete(ResResultUtils.success(submit(data)));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @Override
    public boolean batchSubmit(Map<String, List<Log>> datums) {
        for (Map.Entry<String, List<Log>> entry : datums.entrySet()) {
            final BizProcessor processor = bizProcessorMap.get(entry.getKey());
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
