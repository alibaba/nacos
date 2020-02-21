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

package com.alibaba.nacos.core.distributed;

import com.alibaba.nacos.consistency.Config;
import com.alibaba.nacos.consistency.ConsistencyProtocol;
import com.alibaba.nacos.consistency.LogProcessor;
import com.alibaba.nacos.consistency.ProtocolMetaData;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public abstract class AbstractConsistencyProtocol<T extends Config, L extends LogProcessor> implements ConsistencyProtocol<T> {

    protected final ProtocolMetaData metaData = new ProtocolMetaData();

    protected Map<String, L> dispatcherMap = Collections.synchronizedMap(new HashMap<>());

    public void loadLogDispatcher(List<L> logProcessors) {
        logProcessors.forEach(logDispatcher -> dispatcherMap.put(logDispatcher.bizInfo(), logDispatcher));
    }

    protected Map<String, L> allProcessor() {
        return dispatcherMap;
    }

    @Override
    public ProtocolMetaData protocolMetaData() {
        return this.metaData;
    }

    protected Object getVIfMapByRecursive(Object o, int index, String... keys) {
        if (index >= keys.length) {
            return o;
        }
        if (o.getClass().isAssignableFrom(Map.class)) {
            return getVIfMapByRecursive(((Map) o).get(keys[index]), index + 1, keys);
        }
        return null;
    }

}
