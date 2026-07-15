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

import java.util.Objects;

/**
 * Neutral subtype registration record for JSON adapters.
 *
 * @author nacos
 */
public final class NacosJsonSubtype {
    
    private final Class<?> baseType;
    
    private final Class<?> subtype;
    
    private final String typeName;
    
    /**
     * Create a new subtype registration.
     *
     * @param baseType base type
     * @param subtype subtype class
     * @param typeName wire type name
     */
    public NacosJsonSubtype(Class<?> baseType, Class<?> subtype, String typeName) {
        if (baseType == null) {
            throw new IllegalArgumentException("baseType must not be null");
        }
        if (subtype == null) {
            throw new IllegalArgumentException("subtype must not be null");
        }
        if (typeName == null || typeName.length() == 0) {
            throw new IllegalArgumentException("typeName must not be empty");
        }
        this.baseType = baseType;
        this.subtype = subtype;
        this.typeName = typeName;
    }
    
    /**
     * Return base type.
     *
     * @return base type
     */
    public Class<?> getBaseType() {
        return baseType;
    }
    
    /**
     * Return subtype.
     *
     * @return subtype
     */
    public Class<?> getSubtype() {
        return subtype;
    }
    
    /**
     * Return wire type name.
     *
     * @return wire type name
     */
    public String getTypeName() {
        return typeName;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NacosJsonSubtype)) {
            return false;
        }
        NacosJsonSubtype that = (NacosJsonSubtype) o;
        return Objects.equals(baseType, that.baseType)
            && Objects.equals(subtype, that.subtype)
            && Objects.equals(typeName, that.typeName);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(baseType, subtype, typeName);
    }
}
