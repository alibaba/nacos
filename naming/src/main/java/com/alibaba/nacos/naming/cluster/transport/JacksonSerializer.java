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

package com.alibaba.nacos.naming.cluster.transport;

import com.alibaba.nacos.common.utils.JacksonUtils;
import org.springframework.stereotype.Component;

/**
 * Use Jackson to serialize data.
 *
 * @author yangyi
 */
@Component
public class JacksonSerializer implements Serializer {
    
    private static final String TIMESTAMP_KEY = "timestamp";
    
    private static final String KEY = "key";
    
    private static final String VALUE = "value";
    
    @Override
    public <T> byte[] serialize(T data) {
        return JacksonUtils.toJsonBytes(data);
    }
    
    @Override
    public <T> T deserialize(byte[] data, Class<T> clazz) {
        return JacksonUtils.toObj(data, clazz);
    }
}
