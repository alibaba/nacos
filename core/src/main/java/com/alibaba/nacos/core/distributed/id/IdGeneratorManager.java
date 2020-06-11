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

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.springframework.stereotype.Component;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("PMD.UndefineMagicConstantRule")
@Component
public class IdGeneratorManager {

    private final Map<String, IdGenerator> generatorMap = new ConcurrentHashMap<>();
    private final Function<String, IdGenerator> supplier;

    public IdGeneratorManager() {
        this.supplier = s -> {
            IdGenerator generator;
            ServiceLoader<IdGenerator> loader = ServiceLoader.load(IdGenerator.class);
            Iterator<IdGenerator> iterator = loader.iterator();
            if (iterator.hasNext()) {
                generator = iterator.next();
            } else {
                generator = new SnowFlowerIdGenerator();
            }
            generator.init();
            return generator;
        };
    }

    public void register(String resource) {
        generatorMap.computeIfAbsent(resource, s -> supplier.apply(resource));
    }

    public void register(String... resources) {
        for (String resource : resources) {
            generatorMap.computeIfAbsent(resource, s -> supplier.apply(resource));
        }
    }

    public long nextId(String resource) {
        if (generatorMap.containsKey(resource)) {
            return generatorMap.get(resource).nextId();
        }
        throw new NoSuchElementException("The resource is not registered with the distributed " +
                "ID resource for the time being.");
    }

    public Map<String, IdGenerator> getGeneratorMap() {
        return generatorMap;
    }
}
