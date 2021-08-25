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

package com.alibaba.nacos.istio.api;

import com.alibaba.nacos.istio.mcp.EmptyMcpGenerator;
import com.alibaba.nacos.istio.mcp.ServiceEntryMcpGenerator;
import com.alibaba.nacos.istio.xds.EmptyXdsGenerator;
import com.alibaba.nacos.istio.xds.ServiceEntryXdsGenerator;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.nacos.istio.api.ApiConstants.*;

/**
 * @author special.fy
 */
@Component
public class ApiGeneratorFactory {

    private final Map<String, ApiGenerator<?>> apiGeneratorMap;

    public ApiGeneratorFactory() {
        apiGeneratorMap = new HashMap<>(2);
        // mcp over xds
        apiGeneratorMap.put(SERVICE_ENTRY_PROTO_PACKAGE, ServiceEntryXdsGenerator.getInstance());
        // TODO Support other api generator

        // mcp
        apiGeneratorMap.put(SERVICE_ENTRY_COLLECTION, ServiceEntryMcpGenerator.getInstance());
    }

    public ApiGenerator<?> getApiGenerator(String typeUrl) {
        ApiGenerator<?> apiGenerator = apiGeneratorMap.get(typeUrl);
        return apiGenerator != null ? apiGenerator :
                (typeUrl.startsWith(MCP_PREFIX) ? EmptyMcpGenerator.getInstance() : EmptyXdsGenerator.getInstance());
    }
}
