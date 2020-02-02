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

package com.alibaba.nacos.core.lock;

import com.alibaba.nacos.common.model.ResResult;
import com.alibaba.nacos.core.distributed.Datum;
import com.alibaba.nacos.core.distributed.LogConsumer;
import com.alibaba.nacos.core.executor.ExecutorFactory;
import com.alibaba.nacos.core.executor.NameThreadFactory;
import com.alibaba.nacos.core.utils.ResResultUtils;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Component
final class MemoryLockManager implements LockManager {

    static LockManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private final Map<String, LockAttempt> lockAttemptMap = new ConcurrentHashMap<>(32);

    private final Map<String, LogConsumer> logConsumerMap = new HashMap<>(8);

    private final ScheduledExecutorService executorService = ExecutorFactory
            .newSingleScheduledExecutorService(LockManager.class.getCanonicalName(),
                    new NameThreadFactory("com.alibaba.nacos.core.lock.expire-check"));

    private final Set<String> destroyQueue = new HashSet<>();

    private int destroyCnt = 0;

    public MemoryLockManager() {
        init();
    }

    private void init() {
        executorService.scheduleAtFixedRate(() -> {
            destroyCnt ++;
            boolean remove = destroyCnt == 3;
            if (remove) {
                destroyCnt = 0;
            }
            for (Map.Entry<String, LockAttempt> entry : lockAttemptMap.entrySet()) {
                final String key = entry.getKey();
                final LockAttempt attempt = entry.getValue();
                if (attempt.isExpire()) {
                    destroyQueue.add(key);
                }
            }
            if (remove) {
                for (String removeKey : destroyQueue) {
                    lockAttemptMap.remove(removeKey);
                }
                destroyQueue.clear();
            }
        }, 30, 60, TimeUnit.SECONDS);
    }

    @Override
    public void registerLogConsumer(LogConsumer consumer) {
        LogConsumer lock = new LockConsumer(this);
        LogConsumer unlock = new UnLockConsumer(this);
        LogConsumer renew = new ReNewConsumer(this);
        logConsumerMap.put(lock.operation(), lock);
        logConsumerMap.put(unlock.operation(), unlock);
        logConsumerMap.put(renew.operation(), renew);
    }

    @Override
    public void deregisterLogConsumer(String operation) {
        logConsumerMap.remove(operation);
    }

    @Override
    public <T> T getData(String key) {
        return null;
    }

    @Override
    public ResResult<Boolean> onApply(Datum datum) {
        final String operation = datum.getOperation();
        if (Objects.isNull(LockOperation.sourceOf(operation))) {
            return ResResultUtils.failed("The lock operation is not supported");
        }
        final LogConsumer consumer = logConsumerMap.get(operation);
        try {
            return consumer.onAccept(datum);
        } catch (Exception e) {
            return ResResultUtils.failed(e.getLocalizedMessage());
        }
    }

    @Override
    public Collection<LogConsumer> allLogConsumer() {
        return logConsumerMap.values();
    }

    @Override
    public String bizInfo() {
        return "LOCK";
    }

    @Override
    public boolean interest(String key) {
        return key.contains("LOCK");
    }

    @Override
    public Map<String, LockAttempt> getLockAttempts() {
        return lockAttemptMap;
    }

    @Override
    public DistributedLock newLock(String key) {
        return new DefaultDistributeLock(key);
    }

    @Override
    public void reNew(String key) {
        destroyQueue.remove(key);
        final LockAttempt attempt = lockAttemptMap.get(key);
        attempt.setExpireTimeMs(attempt.getExpireTimeMs() + DistributedLock.LIFE_TIME);
    }

    private static class SingletonHolder {

        private static final LockManager INSTANCE = new MemoryLockManager();

    }
}
