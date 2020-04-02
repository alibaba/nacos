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

import com.alibaba.nacos.consistency.Config;
import com.alibaba.nacos.consistency.ConsistencyProtocol;
import com.alibaba.nacos.consistency.IdGenerator;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Observable;
import java.util.Observer;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.alibaba.nacos.consistency.LogFuture;
import com.alibaba.nacos.consistency.ProtocolMetaData;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.cp.Constants;
import com.alibaba.nacos.consistency.cp.LogProcessor4CP;
import com.alibaba.nacos.consistency.entity.GetRequest;
import com.alibaba.nacos.consistency.entity.GetResponse;
import com.alibaba.nacos.consistency.entity.Log;
import com.alibaba.nacos.consistency.snapshot.SnapshotOperation;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.alibaba.nacos.core.utils.Loggers;
import org.springframework.stereotype.Component;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("PMD.UndefineMagicConstantRule")
@Component
public class IdGeneratorManager extends LogProcessor4CP {

    private final Map<String, IdGenerator> generatorMap = new ConcurrentHashMap<>();
    private final Function<String, IdGenerator> supplier;
    private CPProtocol cpProtocol;
    private GetResponse emptyResponse = GetResponse.newBuilder().build();
    private LogFuture emptyFuture = LogFuture.success(null);

    public IdGeneratorManager() {
        this.supplier = s -> {
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
    }

    public Map<String, Map<Object, Object>> idGeneratorInfo() {
        return generatorMap.entrySet().stream()
                .collect(HashMap::new, (m, e) -> m.put(e.getKey(), e.getValue().info()), HashMap::putAll);
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

    @Override
    protected void afterInject(ConsistencyProtocol<? extends Config> protocol) {
        super.afterInject(protocol);
        this.cpProtocol = (CPProtocol) protocol;
        this.cpProtocol.protocolMetaData()
                .subscribe(group(), Constants.TERM_META_DATA, new Observer() {
                    @Override
                    public void update(Observable o, Object arg) {
                        long term;
                        if (arg == null) {
                            term = 0l;
                        } else {
                            term = Long.parseLong(String.valueOf(arg));
                        }
                        long dataCenterId = term % SnakeFlowerIdGenerator.MAX_DATA_CENTER_ID;
                        SnakeFlowerIdGenerator.setDataCenterId(dataCenterId);
                    }
                });
    }

    @Override
    public GetResponse getData(GetRequest request) {
        return emptyResponse;
    }

    @Override
    public LogFuture onApply(Log log) {
        return emptyFuture;
    }

    @Override
    public void onError(Throwable throwable) {
        Loggers.ID_GENERATOR.error("An error occurred while onApply for ID, error : {}", throwable);
    }

    @Override
    public String group() {
        return "id_generator";
    }

    @Override
    public List<SnapshotOperation> loadSnapshotOperate() {
        return Collections.emptyList();
    }
}
