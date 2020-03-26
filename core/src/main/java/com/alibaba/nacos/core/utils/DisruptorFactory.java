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

package com.alibaba.nacos.core.utils;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class DisruptorFactory {

    // Internal disruptor buffer size. For applications with high write throughput,
    // this value needs to be increased appropriately. default value is 16384

    private static int ringBufferSize = 16384;

    static {
        String ringBufferSizeProperty = "com.alibaba.nacos.core.notify.ringBufferSize";
        String val = System.getProperty(ringBufferSizeProperty, "16384");
        ringBufferSize = Integer.parseInt(val);
    }

    public static <T> Disruptor<T> build(EventFactory<T> factory, Class<T> name) {
        return build(factory, name, ProducerType.MULTI, new BlockingWaitStrategy());
    }

    public static <T> Disruptor<T> build(EventFactory<T> factory, Class<T> name,
                                         ProducerType type) {
        return build(factory, name, type, new BlockingWaitStrategy());
    }

    public static <T> Disruptor<T> build(EventFactory<T> factory, Class<T> name,
                                         ProducerType type, WaitStrategy waitStrategy) {
        return build(ringBufferSize, factory, name, type, waitStrategy);
    }

    public static <T> Disruptor<T> build(int ringBufferSize, EventFactory<T> factory, Class<T> name,
                                         ProducerType type, WaitStrategy waitStrategy) {
        return new Disruptor<>(factory, ringBufferSize, new ThreadFactory() {

            private final AtomicInteger nextId = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                String namePrefix = name.getSimpleName() + "-Disruptor" + "-";
                String name1 = namePrefix + nextId.getAndDecrement();
                return new Thread(r, name1);
            }
        }, type, waitStrategy);
    }

}