/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.env.convert;

import java.util.HashMap;
import java.util.Map;
import java.util.MissingFormatArgumentException;

/**
 * default converters.
 * @author onewe
 */
public class CompositeConverter {
    
    private final Map<Class<?>, AbstractPropertyConverter<?>> converterRegistry = new HashMap<>();
    
    public CompositeConverter() {
        converterRegistry.put(Boolean.class, new BooleanConverter());
        converterRegistry.put(Integer.class, new IntegerConverter());
        converterRegistry.put(Long.class, new LongConverter());
    }
    
    /**
     * convert property to target type.
     * @param property the property gets from environments
     * @param targetClass target class object
     * @param <T> target type
     * @return the object of target type
     */
    public <T> T convert(String property, Class<T> targetClass) {
        final AbstractPropertyConverter<?> converter = converterRegistry.get(targetClass);
        if (converter == null) {
            throw new MissingFormatArgumentException("converter not found, can't convert from String to " + targetClass.getCanonicalName());
        }
        return (T) converter.convert(property);
    }
    
}
