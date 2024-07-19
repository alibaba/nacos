/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.address;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.google.common.base.Preconditions;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Server list provider ordered SPI loader.
 * 
 * @author totalo 
 */
public class ServerListProviderOrderedSPILoader {
    
    public static Collection<ServerListProvider> loadReverseOrderService() {
        return load(Comparator.reverseOrder());
    }
    
    public static Collection<ServerListProvider> load(final Comparator<Integer> comparator) {
        Map<Integer, ServerListProvider> result = new TreeMap<>(comparator);
        for (ServerListProvider each : NacosServiceLoader.load(ServerListProvider.class)) {
            Preconditions.checkArgument(!result.containsKey(each.getOrder()), "Found same order `%s` with `%s` and `%s`", each.getOrder(), result.get(each.getOrder()), each);
            result.put(each.getOrder(), each);
        }
        return result.values();
    }
}
