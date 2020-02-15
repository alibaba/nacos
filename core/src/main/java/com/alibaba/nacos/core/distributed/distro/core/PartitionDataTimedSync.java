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

package com.alibaba.nacos.core.distributed.distro.core;

import com.alibaba.nacos.core.distributed.distro.AbstractDistroKVStore;

import java.util.Map;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class PartitionDataTimedSync {

    private final Map<String, AbstractDistroKVStore> distroStores;

    public PartitionDataTimedSync(Map<String, AbstractDistroKVStore> distroStores) {
        this.distroStores = distroStores;
    }

    public void start() {

    }

    public void shutdown() {

    }

    private class Worker implements Runnable {

        @Override
        public void run() {

        }
    }
}
