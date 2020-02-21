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

import com.alibaba.nacos.consistency.IdGenerator;
import com.alibaba.nacos.core.utils.SnakeFlowerIdHelper;

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class IdGeneratorManager {

    private static final Map<String, IdGenerator> ID_GENERATOR_MAP = new ConcurrentHashMap<>();

    private static int DATA_CENTER_ID = 1;

    private static int WORKER_ID = 1;

    static {

        // Snowflake algorithm default parameter information

        String valForDataCenter = System.getProperty("nacos。snowflake.data-center", "1");
        String valForWorker = System.getProperty("nacos.snowflake.worker", "1");

        DATA_CENTER_ID = Integer.parseInt(valForDataCenter);
        WORKER_ID = Integer.parseInt(valForWorker);
    }

    private static final Supplier<IdGenerator> SUPPLIER = () -> {
        IdGenerator generator;
        ServiceLoader<IdGenerator> loader = ServiceLoader.load(IdGenerator.class);
        Iterator<IdGenerator> iterator = loader.iterator();
        if (iterator.hasNext()) {
            generator = iterator.next();
        } else {
            generator = new SnakeFlowerIdGenerator();
        }
        generator.init();
        return generator;
    };

    public static void register(String resource) {
        ID_GENERATOR_MAP.computeIfAbsent(resource, s -> SUPPLIER.get());
    }

    public static void register(String... resources) {
        for (String resource : resources) {
            ID_GENERATOR_MAP.computeIfAbsent(resource, s -> SUPPLIER.get());
        }
    }

    public static long nextId(String resource) {
        if (ID_GENERATOR_MAP.containsKey(resource)) {
            return ID_GENERATOR_MAP.get(resource).nextId();
        }
        throw new NoSuchElementException("The resource is not registered with the distributed " +
                "ID resource for the time being.");
    }

    public static class SnakeFlowerIdGenerator implements IdGenerator {

        SnakeFlowerIdHelper helper;

        @Override
        public void init() {
            helper = new SnakeFlowerIdHelper(DATA_CENTER_ID, WORKER_ID);
        }

        @Override
        public long nextId() {
            return helper.nextId();
        }
    }

}
