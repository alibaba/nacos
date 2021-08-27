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

package com.alibaba.nacos.istio.common;

import io.grpc.stub.StreamObserver;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AbstractConnection maintains the life cycle of the connection.
 *
 * @author special.fy
 */
public abstract class AbstractConnection<MessageT> {

    private static AtomicLong connectIdGenerator = new AtomicLong(0);

    private String connectionId;

    protected StreamObserver<MessageT> streamObserver;

    private final Map<String, WatchedStatus> watchedResources;

    public AbstractConnection(StreamObserver<MessageT> streamObserver) {
        this.streamObserver = streamObserver;
        this.watchedResources = new HashMap<>(1 << 4);
    }

    public void setConnectionId(String clientId) {
        long id = connectIdGenerator.getAndIncrement();
        this.connectionId = clientId + "-" + id;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public void addWatchedResource(String resourceType, WatchedStatus watchedStatus) {
        watchedResources.put(resourceType, watchedStatus);
    }

    public WatchedStatus getWatchedStatusByType(String resourceType) {
        return watchedResources.get(resourceType);
    }

    /**
     * Push data to grpc connection.
     *
     * @param message response
     * @param watchedStatus watched status
     */
    public abstract void push(MessageT message, WatchedStatus watchedStatus);
}
