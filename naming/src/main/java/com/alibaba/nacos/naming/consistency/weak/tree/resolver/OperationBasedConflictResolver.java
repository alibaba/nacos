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
package com.alibaba.nacos.naming.consistency.weak.tree.resolver;

import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.weak.Operation;

/**
 * Public interface for a operation based conflict resolver.
 *
 * @author lostcharlie
 */
public interface OperationBasedConflictResolver {
    /**
     * Apply target operation on local data replica and resolve conflicts for concurrent operations
     *
     * @param current current value of the datum
     * @param toApply the operation to apply
     */
    void merge(Datum current, Operation toApply);
}
