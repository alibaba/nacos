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

import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.common.Serializer;
import io.protostuff.ByteArrayInput;
import io.protostuff.Input;
import io.protostuff.LinkedBuffer;
import io.protostuff.Output;
import io.protostuff.ProtostuffOutput;
import io.protostuff.Schema;
import io.protostuff.WriteSession;
import io.protostuff.runtime.RuntimeSchema;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class ProtoBufSerializer implements Serializer {

    // Need to control the amount of LinkedBuffer held by ThreadLocal

    private static final AtomicInteger NOW_HOLD = new AtomicInteger(0);

    private static final ThreadLocal<LinkedBuffer> CACHE = ThreadLocal.withInitial(() -> {
        NOW_HOLD.incrementAndGet();
        return LinkedBuffer.allocate();
    });

    private static final int MAX_HOLD = 256;

    @Override
    public <T> T deSerialize(byte[] data, Class<T> cls) {
        Schema<T> schema = RuntimeSchema.getSchema(cls);
        T msg = schema.newMessage();

        Input input = new ByteArrayInput(data, 0, data.length, true);
        try {
            schema.mergeFrom(input, msg);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return msg;
    }

    @Override
    public <T> T deSerialize(byte[] data, TypeReference<T> reference) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T deSerialize(byte[] data, String classFullName) {
        try {

            Class<?> cls;

            if (!CLASS_CACHE.containsKey(classFullName)) {
                synchronized (MONITOR) {
                    if (!CLASS_CACHE.containsKey(classFullName)) {
                        cls = Class.forName(classFullName);
                        CLASS_CACHE.put(classFullName, cls);
                    }
                }
            }

            cls = CLASS_CACHE.get(classFullName);

            return (T) deSerialize(data, cls);
        } catch (Exception ignore) {
            return null;
        }
    }

    @Override
    public <T> byte[] serialize(T obj) {
        Schema<T> schema = RuntimeSchema.getSchema((Class<T>) obj.getClass());

        LinkedBuffer buffer = CACHE.get();

        try {
            Output output = new ProtostuffOutput(buffer);
            schema.writeTo(output, obj);
            return ((WriteSession) output).toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (NOW_HOLD.get() > MAX_HOLD) {
                CACHE.remove();
            } else {
                buffer.clear();
            }
        }
    }

    @Override
    public String name() {
        return "protostuff";
    }
}
