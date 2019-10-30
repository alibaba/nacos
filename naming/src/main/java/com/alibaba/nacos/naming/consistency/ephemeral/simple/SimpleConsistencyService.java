/*
 * Copyright 1999-2019 Alibaba Group Holding Ltd.
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
package com.alibaba.nacos.naming.consistency.ephemeral.simple;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.naming.consistency.ConsistencyService;

/**
 * Interface for the consistency service which supports performing an atomic operation on local data replica
 *
 * @author lostcharlie
 */
public interface SimpleConsistencyService extends ConsistencyService {

    /**
     * Tell if the consistency service supports performing an operation on key-value store
     *
     * @return true if it supports the "performOperation" call
     */
    boolean supportPerformOperation();

    /**
     * Perform an atomic operation on the key-value store
     *
     * @param key       the key of the data
     * @param operation the atomic operation
     * @throws NacosException
     */
    void performOperation(String key, Operation operation) throws NacosException;
}
