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

import com.alibaba.nacos.common.utils.Preconditions;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

/**
 * Encapsulates third party tools for generics acquisition.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class GenericType<T> {
    
    private static final long serialVersionUID = -2103808581228167629L;
    
    private final Type runtimeType;
    
    final Type capture() {
        Type superclass = getClass().getGenericSuperclass();
        Preconditions.checkArgument(superclass instanceof ParameterizedType, "%s isn't parameterized", superclass);
        return ((ParameterizedType) superclass).getActualTypeArguments()[0];
    }
    
    protected GenericType() {
        this.runtimeType = capture();
        if (runtimeType instanceof TypeVariable) {
            throw new IllegalArgumentException("runtimeType must be ParameterizedType Class");
        }
    }
    
    /**
     * Returns the represented type.
     */
    public final Type getType() {
        return runtimeType;
    }
}
