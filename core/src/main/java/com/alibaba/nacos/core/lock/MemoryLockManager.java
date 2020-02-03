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

import com.alibaba.nacos.core.executor.ExecutorFactory;
import com.alibaba.nacos.core.executor.NameThreadFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@ConditionalOnMissingBean(value = LockManager.class)
@Component
final class MemoryLockManager extends BaseLockManager {

    private final Map<String, LockAttempt> lockAttemptMap = new ConcurrentHashMap<>(32);

    private final Set<String> destroyQueue = new HashSet<>();

    private int destroyCnt = 0;

    private final ScheduledExecutorService executorService = ExecutorFactory
            .newSingleScheduledExecutorService(LockManager.class.getCanonicalName(),
                    new NameThreadFactory("com.alibaba.nacos.core.lock.expire-check"));

    @PostConstruct
    protected void init() {
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
    public Map<String, LockAttempt> getLockAttempts() {
        return lockAttemptMap;
    }

    @Override
    public void reNew(String key) {
        destroyQueue.remove(key);
        final LockAttempt attempt = lockAttemptMap.get(key);
        attempt.setExpireTimeMs(attempt.getExpireTimeMs() + DistributedLock.LIFE_TIME);
    }

    @Override
    public DistributedLock newLock(String key) {
        return new DefaultDistributeLock(key);
    }

}
