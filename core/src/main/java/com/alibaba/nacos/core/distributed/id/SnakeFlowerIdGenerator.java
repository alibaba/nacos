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
package com.alibaba.nacos.core.distributed.id;

import com.alibaba.nacos.consistency.IdGenerator;
import com.alibaba.nacos.core.exception.SnakflowerException;
import com.alibaba.nacos.core.utils.ConvertUtils;
import java.util.HashMap;
import java.util.Map;

/**
 * copy from https://blog.csdn.net/qq_38366063/article/details/83691424
 *
 * @author <a href="mailto:liaochunyhm@live.com">liaochuntao</a>
 */
public class SnakeFlowerIdGenerator implements IdGenerator {

    /**
     * Start time intercept (2018-08-05 08:34)
     */
    private final long twepoch = 1533429269000L;
    private final long workerIdBits = 5L;
    private final long datacenterIdBits = 5L;
    private final long maxWorkerId = -1L ^ (-1L << workerIdBits);
    private final long maxDatacenterId = -1L ^ (-1L << datacenterIdBits);

    private final long sequenceBits = 12L;
    private final long workerIdShift = sequenceBits;
    private final long datacenterIdShift = sequenceBits + workerIdBits;
    private final long timestampLeftShift = sequenceBits + workerIdBits
            + datacenterIdBits;
    private final long sequenceMask = -1L ^ (-1L << sequenceBits);
    private volatile long currentId;
    private long workerId;
    private long datacenterId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    @Override
    public void init() {

        // Snowflake algorithm default parameter information

        int dataCenterId = ConvertUtils.toInt(System.getProperty("nacos.core.snowflake.data-center"), 1);
        int workerId = ConvertUtils.toInt(System.getProperty("nacos.core.snowflake.worker-id"), 1);

        initialize(dataCenterId, workerId);
    }

    @Override
    public long currentId() {
        return currentId;
    }

    @Override
    public synchronized long nextId() {
        long timestamp = timeGen();

        if (timestamp < lastTimestamp) {
            throw new SnakflowerException(String.format(
                    "Clock moved backwards.  Refusing to generate id for %d milliseconds",
                    lastTimestamp - timestamp));
        }

        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & sequenceMask;
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        }
        else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        currentId = ((timestamp - twepoch) << timestampLeftShift)
                | (datacenterId << datacenterIdShift)
                | (workerId << workerIdShift)
                | sequence;
        return currentId;
    }

    @Override
    public Map<Object, Object> info() {
        Map<Object, Object> info = new HashMap<>(4);
        info.put("currentId", currentId);
        info.put("dataCenterId", datacenterId);
        info.put("workerId", workerId);
        return info;
    }

    // ==============================Constructors=====================================

    /**
     * init
     *
     * @param workerId     worker id (0~31)
     * @param datacenterId data center id (0~31)
     */
    public void initialize(long workerId, long datacenterId) {
        if (workerId > maxWorkerId || workerId < 0) {
            throw new IllegalArgumentException(String.format(
                    "worker Id can't be greater than %d or less than 0", maxWorkerId));
        }
        if (datacenterId > maxDatacenterId || datacenterId < 0) {
            throw new IllegalArgumentException(
                    String.format("datacenter Id can't be greater than %d or less than 0",
                            maxDatacenterId));
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    /**
     * Block to the next millisecond until a new timestamp is obtained
     *
     * @param lastTimestamp 上次生成ID的时间截
     * @return 当前时间戳
     */
    protected long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    /**
     * Returns the current time in milliseconds
     *
     * @return 当前时间(毫秒)
     */
    protected long timeGen() {
        return System.currentTimeMillis();
    }
}