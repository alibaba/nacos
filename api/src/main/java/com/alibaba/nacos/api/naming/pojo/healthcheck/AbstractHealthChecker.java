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
package com.alibaba.nacos.api.naming.pojo.healthcheck;

import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker.None;
import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Http;
import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Mysql;
import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Tcp;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

/**
 * @author nkorange
 */

@JsonTypeInfo(use = Id.NAME, property = "type", defaultImpl = None.class)
@JsonSubTypes({
    @JsonSubTypes.Type(name = Http.TYPE, value = Http.class),
    @JsonSubTypes.Type(name = Mysql.TYPE, value = Mysql.class),
    @JsonSubTypes.Type(name = Tcp.TYPE, value = Tcp.class)
})
public abstract class AbstractHealthChecker implements Cloneable {

    @JsonIgnore
    protected final String type;

    protected AbstractHealthChecker(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    /**
     * Clone all fields of this instance to another one.
     *
     * @return Another instance with exactly the same fields
     * @throws CloneNotSupportedException clone not supported exception
     */
    @Override
    public abstract AbstractHealthChecker clone() throws CloneNotSupportedException;

    /**
     * Default implementation of Health checker.
     */
    public static class None extends AbstractHealthChecker {

        public static final String TYPE = "NONE";

        public None() {
            super(TYPE);
        }

        @Override
        public AbstractHealthChecker clone() throws CloneNotSupportedException {
            return new None();
        }
    }
}
