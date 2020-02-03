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

package com.alibaba.nacos.config.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.common.model.ResResult;
import com.alibaba.nacos.core.distributed.ConsistencyProtocol;
import com.alibaba.nacos.core.distributed.NDatum;
import com.alibaba.nacos.core.distributed.raft.RaftConfig;
import com.alibaba.nacos.core.distributed.raft.jraft.JRaftProtocol;
import com.alibaba.nacos.core.utils.SpringUtils;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class DistributeProtocolAware {

    private ConsistencyProtocol<RaftConfig> protocol;

    public DistributeProtocolAware() {
        protocol = SpringUtils.getBean(JRaftProtocol.class);
    }

    protected  <T> void submit(final String key,
                            final T data,
                            final String operation,
                            final Map<String, String> extendInfo) {

        final NDatum datum = NDatum.builder()
                .operation(operation)
                .className(data.getClass().getCanonicalName())
                .key(key)
                .data(JSON.toJSONBytes(data))
                .extendInfo(extendInfo)
                .build();

        try {
            protocol.submit(datum);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected  <T> CompletableFuture<ResResult<Boolean>> submitAsync(final String key,
                                                                  final T data,
                                                                  final String operation,
                                                                  final Map<String, String> extendInfo) {

        final NDatum datum = NDatum.builder()
                .operation(operation)
                .className(data.getClass().getCanonicalName())
                .key(key)
                .data(JSON.toJSONBytes(data))
                .extendInfo(extendInfo)
                .build();

        return protocol.submitAsync(datum);
    }

}
