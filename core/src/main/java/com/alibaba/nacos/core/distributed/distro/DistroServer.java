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

import com.alibaba.nacos.core.cluster.NodeManager;
import com.alibaba.nacos.core.distributed.distro.sync.DataSyncer;
import com.alibaba.nacos.core.distributed.distro.sync.PartitionDataTimedSync;
import com.alibaba.nacos.core.utils.SpringUtils;

import java.util.List;
import java.util.Objects;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class DistroServer {

    private DataSyncer dataSyncer;
    private PartitionDataTimedSync timedSync;

    private final List<AbstractDistroKVStore> distroStores;

    public DistroServer(List<AbstractDistroKVStore> distroStores) {
        this.distroStores = distroStores;
    }

    public void start() {
        this.timedSync = new PartitionDataTimedSync(this.distroStores);
        this.dataSyncer = new DataSyncer(SpringUtils.getBean(NodeManager.class), distroStores);

        this.dataSyncer.start();
        this.timedSync.start();
    }

    public void shutdown() {
        if (Objects.nonNull(timedSync)) {
            timedSync.shutdown();
        }
        if (Objects.nonNull(dataSyncer)) {
            dataSyncer.shutdown();
        }
    }
}
