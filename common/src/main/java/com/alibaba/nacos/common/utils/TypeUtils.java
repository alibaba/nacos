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

package com.alibaba.nacos.common.utils;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * type utils.
 *
 * @author zzq
 */
public class TypeUtils {

    private TypeUtils() {
    }
    
    /**
     * Create a parameterized type instance.
     *
     * @param raw           raw class
     * @param typeArguments the types used for parameterization
     * @return {@link ParameterizedType}
     */
    public static ParameterizedType parameterize(final Class<?> raw, final Type... typeArguments) {
        checkParameterizeMethodParameter(raw, typeArguments);
        return new ParameterizedTypeImpl(raw, raw.getEnclosingClass(), typeArguments);
    }
    
    /**
     * Check parameterize method parameter.
     *
     * @param raw           raw class
     * @param typeArguments the types used for parameterization
     */
    private static void checkParameterizeMethodParameter(Class<?> raw, final Type... typeArguments) {
        if (raw == null) {
            throw new NullPointerException("raw cannot be null");
        }
        if (typeArguments == null) {
            throw new NullPointerException("typeArguments cannot be null");
        }
        if (typeArguments.length != raw.getTypeParameters().length) {
            throw new IllegalArgumentException(
                    String.format("invalid number of type parameters specified: expected %s, got %s",
                            raw.getTypeParameters().length, typeArguments.length));
        }
        
        for (int i = 0; i < typeArguments.length; i++) {
            if (typeArguments[i] == null) {
                throw new IllegalArgumentException("There can be no null in typeArguments");
            }
        }
        
    }
    
    /**
     * ParameterizedType implementation class.
     */
    private static final class ParameterizedTypeImpl implements ParameterizedType {
        
        /**
         * type.
         */
        private final Class<?> raw;
        
        /**
         * owner type to use, if any.
         */
        private final Type useOwner;
        
        /**
         * formal type arguments.typeArguments
         */
        private final Type[] typeArguments;
        
        private ParameterizedTypeImpl(final Class<?> raw, final Type useOwner, final Type[] typeArguments) {
            this.raw = raw;
            this.useOwner = useOwner;
            this.typeArguments = typeArguments;
        }
        
        @Override
        public Type getRawType() {
            return raw;
        }
        
        @Override
        public Type getOwnerType() {
            return useOwner;
        }
        
        @Override
        public Type[] getActualTypeArguments() {
            return typeArguments.clone();
        }
        
        @Override
        public String toString() {
            final StringBuilder buf = new StringBuilder();
            
            buf.append(raw.getName());
            buf.append('<');
            buf.append(typeArguments[0].getTypeName());
            for (int i = 1; i < typeArguments.length; i++) {
                buf.append(", ");
                buf.append(typeArguments[i].getTypeName());
            }
            buf.append('>');
            
            return buf.toString();
        }
    }
    
}
