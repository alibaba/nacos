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

package com.alibaba.nacos.core.distributed.id;

import com.alibaba.nacos.core.cluster.NodeManager;
import com.alibaba.nacos.core.utils.SnakeFlowerIdHelper;
import com.alibaba.nacos.core.utils.SystemUtils;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class DistributeIDManager {

    private static NodeManager nodeManager;

    private static final AtomicInteger WORKER_ID = new AtomicInteger(1);

    private static final Map<String, SnakeFlowerIdHelper> SNAKE_FLOWER_ID_HELPER_MAP = new ConcurrentHashMap<>();

    private static int DATA_CENTER_ID = -1;

    private static final int MAX_WORKER_ID = 31;

    public static void init(NodeManager nodeManager) {
        DistributeIDManager.nodeManager = nodeManager;
        DATA_CENTER_ID = nodeManager.indexOf(SystemUtils.LOCAL_IP);
    }

    public static void register(String resource) {
        int workerId = WORKER_ID.get();
        SNAKE_FLOWER_ID_HELPER_MAP.computeIfAbsent(resource, s -> new SnakeFlowerIdHelper(DATA_CENTER_ID, workerId));
    }

    public static void register(String... resources) {
        int workerId = WORKER_ID.get();
        for (String resource : resources) {
            SNAKE_FLOWER_ID_HELPER_MAP.computeIfAbsent(resource, s -> new SnakeFlowerIdHelper(DATA_CENTER_ID, workerId));
        }
    }

    public static long nextId(String resource) {
        if (SNAKE_FLOWER_ID_HELPER_MAP.containsKey(resource)) {
            return SNAKE_FLOWER_ID_HELPER_MAP.get(resource).nextId();
        }
        throw new NoSuchElementException("The resource is not registered with the distributed " +
                "ID resource for the time being.");
    }

}
