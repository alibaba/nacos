/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.alibaba.nacos.dns.generator;

import com.alibaba.nacos.naming.core.Service;

/**
 * @author paderlol
 * @date 2019年07月28日, 16:31:16
 */
public interface DomainGenerator {

    /**
     * @description The constant DOMAIN_SUFFIX.
     */
    String DOMAIN_SUFFIX = ".nacos.local";

    /**
     * Is match boolean.
     *
     * @param service the service
     * @return the boolean
     * @description
     * @author paderlol
     * @date 2019年07月28日, 16:31:16
     */
    public boolean isMatch(Service service);

    /**
     * Create string.
     *
     * @param service the service
     * @return the string
     * @description
     * @author paderlol
     * @date 2019年07月28日, 16:31:16
     */
    public String create(Service service);
}
