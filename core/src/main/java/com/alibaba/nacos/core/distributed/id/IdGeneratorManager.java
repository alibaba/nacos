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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Component
public class IdGeneratorManager {

    private static final Map<String, IdGenerator> ID_GENERATOR_MAP = new ConcurrentHashMap<>();

    private static final String ID_TYPE = System.getProperty("nacos.core.idGenerator.type", "default");

    private static final Function<String, IdGenerator> SUPPLIER = s -> {
        IdGenerator generator;
        ServiceLoader<IdGenerator> loader = ServiceLoader.load(IdGenerator.class);
        Iterator<IdGenerator> iterator = loader.iterator();
        if (iterator.hasNext()) {
            generator = iterator.next();
        } else {
            if (Objects.equals(ID_TYPE, "snakeflower")) {
                generator = new SnakeFlowerIdGenerator();
            } else {
                generator = new DefaultIdGenerator(s);
            }
        }
        generator.init();
        return generator;
    };

    public Map<String, Long> idGeneratorInfo() {
        return ID_GENERATOR_MAP.entrySet().stream()
                .collect(HashMap::new, (m, e) -> m.put(e.getKey(), e.getValue().currentId()), HashMap::putAll);
    }

    public void register(String resource) {
        ID_GENERATOR_MAP.computeIfAbsent(resource, s -> SUPPLIER.apply(resource));
    }

    public void register(String... resources) {
        for (String resource : resources) {
            ID_GENERATOR_MAP.computeIfAbsent(resource, s -> SUPPLIER.apply(resource));
        }
    }

    public long nextId(String resource) {
        if (ID_GENERATOR_MAP.containsKey(resource)) {
            return ID_GENERATOR_MAP.get(resource).nextId();
        }
        throw new NoSuchElementException("The resource is not registered with the distributed " +
                "ID resource for the time being.");
    }

}
