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

import java.util.Map;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public interface LockManager {

    /**
     * Get all lock information
     *
     * @return {@link Map<String, LockAttempt>}
     */
    Map<String, LockAttempt> getLockAttempts();

    /**
     * create new lock, At this time, there is no competition to lock resources
     *
     * @param key resource
     * @return {@link DistributedLock}
     */
    DistributedLock newLock(String key);

    /**
     * renew lock life time
     *
     * @param key lock key
     */
    void reNew(String key);

    static class LockAttempt {

        // use millisecond

        private long expireTimeMs;

        // use timestamp act as version number

        private long version;

        public long getExpireTimeMs() {
            return expireTimeMs;
        }

        public void setExpireTimeMs(long expireTimeMs) {
            this.expireTimeMs = expireTimeMs;
        }

        public long getVersion() {
            return version;
        }

        public void setVersion(long version) {
            this.version = version;
        }

        public boolean isExpire() {
            return expireTimeMs < System.currentTimeMillis();
        }
    }

}
