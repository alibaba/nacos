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

package com.alibaba.nacos.istio.mcp;

import com.alibaba.nacos.istio.common.AbstractConnection;
import com.alibaba.nacos.istio.common.WatchedStatus;
import com.alibaba.nacos.istio.misc.Loggers;
import io.grpc.stub.StreamObserver;
import istio.mcp.v1alpha1.Mcp;

/**
 * @author special.fy
 */
public class McpConnection extends AbstractConnection<Mcp.Resources> {

    public McpConnection(StreamObserver<Mcp.Resources> streamObserver) {
        super(streamObserver);
    }

    @Override
    public void push(Mcp.Resources response, WatchedStatus watchedStatus) {
        if (Loggers.MAIN.isDebugEnabled()) {
            Loggers.MAIN.debug("Mcp.Resources: {}", response.toString());
        }

        this.streamObserver.onNext(response);

        // Update watched status
        watchedStatus.setLatestVersion(response.getSystemVersionInfo());
        watchedStatus.setLatestNonce(response.getNonce());

        Loggers.MAIN.info("mcp: push, type: {}, connection-id {}, version {}, nonce {}, resource size {}.",
                watchedStatus.getType(),
                getConnectionId(),
                response.getSystemVersionInfo(),
                response.getNonce(),
                response.getResourcesCount());
    }
}
