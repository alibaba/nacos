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
package com.alibaba.nacos.naming.consistency;

import com.alibaba.nacos.naming.pojo.Record;

/**
 * Atomic operation on the key-value store
 *
 * @author lostcharlie
 */
public class Operation {
    private OperationType operationType;
    private Record targetValue;
    private Long realTime;

    public Record getTargetValue() {
        return targetValue;
    }

    public void setTargetValue(Record targetValue) {
        this.targetValue = targetValue;
    }

    public OperationType getOperationType() {
        return operationType;
    }

    public void setOperationType(OperationType operationType) {
        this.operationType = operationType;
    }

    public Long getRealTime() {
        return realTime;
    }

    public void setRealTime(Long realTime) {
        this.realTime = realTime;
    }

    public Operation() {

    }

}
