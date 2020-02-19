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
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.pool.KryoPool;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public final class KryoSerializer implements Serializer {

    private final KryoPool kryoPool;

    public KryoSerializer() {
        kryoPool = new KryoPool.Builder(new KryoFactory()).softReferences().build();
    }

    @Override
    public <T> T deSerialize(byte[] data, Class<T> cls) {
        return kryoPool.run(kryo -> {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(data);
            Input input = new Input(byteArrayInputStream);
            return (T) kryo.readClassAndObject(input);
        });
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
                synchronized (monitor) {
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
    public byte[] serialize(Object obj) {
        return kryoPool.run(kryo -> {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            Output output = new Output(byteArrayOutputStream);
            kryo.writeClassAndObject(output, obj);
            output.close();
            return byteArrayOutputStream.toByteArray();
        });
    }

    @Override
    public String name() {
        return "Kryo";
    }

    private class KryoFactory implements com.esotericsoftware.kryo.pool.KryoFactory {

        @Override
        public Kryo create() {
            Kryo kryo = new Kryo();
            kryo.setRegistrationRequired(false);
            kryo.setInstantiatorStrategy(new Kryo.DefaultInstantiatorStrategy(
                    new org.objenesis.strategy.StdInstantiatorStrategy()));
            return kryo;
        }

    }
}