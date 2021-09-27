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

package com.alibaba.nacos.consistency.serialize;

import com.alibaba.nacos.common.utils.ByteUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.consistency.Serializer;

import java.lang.reflect.Type;

/**
 * Serializer implement by jackson.
 *
 * @author xiweng.yy
 */
public class JacksonSerializer implements Serializer {
    
    private static final String NAME = "JSON";
    
    @Override
    public <T> T deserialize(byte[] data) {
        throw new UnsupportedOperationException("Jackson serializer can't support deserialize json without type");
    }
    
    @Override
    public <T> T deserialize(byte[] data, Class<T> cls) {
        if (ByteUtils.isEmpty(data)) {
            return null;
        }
        return JacksonUtils.toObj(data, cls);
    }
    
    @Override
    public <T> T deserialize(byte[] data, Type type) {
        if (ByteUtils.isEmpty(data)) {
            return null;
        }
        return JacksonUtils.toObj(data, type);
    }
    
    @Override
    public <T> byte[] serialize(T obj) {
        return JacksonUtils.toJsonBytes(obj);
    }
    
    @Override
    public String name() {
        return NAME;
    }
}
