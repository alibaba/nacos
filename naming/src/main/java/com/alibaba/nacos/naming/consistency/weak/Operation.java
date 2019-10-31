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
package com.alibaba.nacos.naming.consistency.weak;

import com.alibaba.nacos.naming.pojo.Record;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Atomic operation on the key-value store
 *
 * @author lostcharlie
 */
public class Operation {
    private UUID uuid;
    private OperationType operationType;
    private Record targetValue;
    private AtomicLong timestamp;

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

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

    public AtomicLong getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(AtomicLong timestamp) {
        this.timestamp = timestamp;
    }

    public Operation() {
        this.setUuid(UUID.randomUUID());
        this.setTimestamp(new AtomicLong(0L));
    }

}
