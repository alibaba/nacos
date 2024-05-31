/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.remote.grpc.filter;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.LinkedList;
import java.util.List;

/**
 * Service Loader for nacos grpc server transport filter.
 *
 * @author xiweng.yy
 */
public class NacosGrpcServerTransportFilterServiceLoader {
    
    /**
     * Load Server Interceptors by type.
     *
     * @param type should be `CLUSTER` or `SDK`
     * @return Server Interceptors for type
     */
    public static List<NacosGrpcServerTransportFilter> loadServerTransportFilters(String type) {
        List<NacosGrpcServerTransportFilter> result = new LinkedList<>();
        for (NacosGrpcServerTransportFilter each : NacosServiceLoader.load(NacosGrpcServerTransportFilter.class)) {
            if (StringUtils.equals(type, each.type())) {
                result.add(each);
            }
        }
        return result;
    }
}
