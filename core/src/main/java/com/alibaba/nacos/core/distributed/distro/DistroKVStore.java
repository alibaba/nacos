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

import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.Config;
import com.alibaba.nacos.consistency.ConsistencyProtocol;
import com.alibaba.nacos.consistency.Log;
import com.alibaba.nacos.consistency.NLog;
import com.alibaba.nacos.consistency.ap.LogProcessor4AP;
import com.alibaba.nacos.consistency.request.GetRequest;
import com.alibaba.nacos.consistency.request.GetResponse;
import com.alibaba.nacos.consistency.store.KVStore;
import java.util.Map;
import java.util.function.BiConsumer;
import org.apache.commons.lang3.StringUtils;
import org.javatuples.Pair;

/**
 * Eventual consistency key-value pair storage
 * Implementation class needs to be discovered by Spring
 *
 * <p>
 * Provides AP consistency internally, that is, the KV data
 * under each node will eventually be consistent, and the AP
 * protocol is shielded. Users only need to use the provided
 * data operation method to enjoy distributed KV based on final
 * consistency
 * </p>
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class DistroKVStore<T> extends KVStore<T> {

    private final KVLogProcessor logProcessor;

    private ConsistencyProtocol<? extends Config> protocol;

    DistroKVStore(String name) {
        this(name, SerializeFactory.getDefault());
    }

    DistroKVStore(String name, Serializer serializer) {
        super(name, serializer);
        this.logProcessor = new KVLogProcessor();
    }

    @Override
    public final boolean put(String key, T data) throws Exception {
        final byte[] putData = serializer.serialize(data);

        final NLog log = NLog.builder()
                .key(key)
                .data(putData)
                .operation(PUT_COMMAND)
                .className(data.getClass().getCanonicalName())
                .addContextValue("source", data)
                .build();

        return logProcessor.commitAutoSetBiz(log);
    }

    @Override
    public final boolean remove(String key) throws Exception {
        final NLog log = NLog.builder()
                .key(key)
                .operation(REMOVE_COMMAND)
                .build();

        return logProcessor.commitAutoSetBiz(log);
    }

    // Loading data, will not trigger AP consistent interface call

    @Override
    public final void load(Map<String, Item> remoteData) {
        remoteData.forEach(new BiConsumer<String, Item>() {
            @Override
            public void accept(String s, Item item) {
                final String key = s;
                final T source = serializer.deSerialize(item.getBytes(), item.getClassName());
                operate(key, Pair.with(source, item.getBytes()), PUT_COMMAND);
            }
        });
    }

    // Provide AP consistency capability for KV storage

    KVLogProcessor getKVLogProcessor() {
        return logProcessor;
    }

    final class KVLogProcessor implements LogProcessor4AP {

        @Override
        public void injectProtocol(ConsistencyProtocol<? extends Config> protocol) {
            DistroKVStore.this.protocol = protocol;
        }

        @Override
        public ConsistencyProtocol<? extends Config> getProtocol() {
            return DistroKVStore.this.protocol;
        }

        @Override
        public <D> GetResponse<D> getData(GetRequest request) {
            try {
                final String key = new String(request.getCtx());
                return GetResponse.<D>builder()
                        .data((D) getByKeyAutoConvert(key))
                        .build();
            } catch (Exception e) {
                return GetResponse.<D>builder()
                        .exceptionName(e.getClass().getName())
                        .errMsg(e.getMessage())
                        .build();
            }
        }

        @Override
        public boolean onApply(Log log) {
            final String operation = log.getOperation();
            final String originKey = log.getKey();
            final NLog nLog = (NLog) log;
            if (StringUtils.equalsIgnoreCase(operation, PUT_COMMAND)) {
                final byte[] data = log.getData();
                final T source = (T) nLog.getContextValue("source");
                operate(originKey, Pair.with(source, data), PUT_COMMAND);
                return true;
            }
            if (StringUtils.equalsIgnoreCase(operation, REMOVE_COMMAND)) {
                operate(originKey, null, REMOVE_COMMAND);
                return true;
            }
            throw new UnsupportedOperationException();
        }

        @Override
        public String bizInfo() {
            return storeName();
        }

    }

}
