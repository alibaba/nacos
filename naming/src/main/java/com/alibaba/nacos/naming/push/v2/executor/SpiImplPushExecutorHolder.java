/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.push.v2.executor;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.naming.pojo.Subscriber;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * SPI push executor holder.
 *
 * @author xiweng.yy
 */
public class SpiImplPushExecutorHolder {
    
    private static final SpiImplPushExecutorHolder INSTANCE = new SpiImplPushExecutorHolder();
    
    private final Set<SpiPushExecutor> pushExecutors;
    
    private SpiImplPushExecutorHolder() {
        pushExecutors = new HashSet<>(NacosServiceLoader.load(SpiPushExecutor.class));
    }
    
    public static SpiImplPushExecutorHolder getInstance() {
        return INSTANCE;
    }
    
    /**
     * Try to find an {@link PushExecutor} implement by SPI which interest to execute this push.
     *
     * @param clientId   client id
     * @param subscriber subscriber infor
     * @return {@link PushExecutor} which interest to execute this push, otherwise {@code Optional.empty()}
     */
    public Optional<SpiPushExecutor> findPushExecutorSpiImpl(String clientId, Subscriber subscriber) {
        for (SpiPushExecutor each : pushExecutors) {
            if (each.isInterest(clientId, subscriber)) {
                return Optional.of(each);
            }
        }
        return Optional.empty();
    }
}
