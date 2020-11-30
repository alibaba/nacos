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

package com.alibaba.nacos.api.naming.pojo.healthcheck.impl;

import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.google.common.base.Objects;

/**
 * Implementation of health checker for TCP.
 *
 * @author yangyi
 */
public class Tcp extends AbstractHealthChecker {
    
    public static final String TYPE = "TCP";
    
    private static final long serialVersionUID = -9116042038157496294L;
    
    public Tcp() {
        super(TYPE);
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(TYPE);
    }
    
    @Override
    public boolean equals(Object obj) {
        return obj instanceof Tcp;
    }
    
    @Override
    public Tcp clone() throws CloneNotSupportedException {
        return new Tcp();
    }
}
