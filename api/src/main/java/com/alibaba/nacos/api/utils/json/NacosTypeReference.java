/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.utils.json;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Captures generic type information for JSON deserialization without exposing
 * concrete JSON provider types.
 *
 * @param <T> target type
 * @author nacos
 */
public abstract class NacosTypeReference<T> {
    
    private final Type type;
    
    /**
     * Create a new type reference and capture generic type from subclass.
     */
    protected NacosTypeReference() {
        Type superClass = getClass().getGenericSuperclass();
        if (superClass instanceof Class) {
            throw new IllegalArgumentException(
                "NacosTypeReference must be created with generic type information.");
        }
        this.type = ((ParameterizedType) superClass).getActualTypeArguments()[0];
    }
    
    /**
     * Return captured generic type.
     *
     * @return captured type
     */
    public Type getType() {
        return type;
    }
}
