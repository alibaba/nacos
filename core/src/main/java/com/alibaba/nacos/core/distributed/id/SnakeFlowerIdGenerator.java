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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

/**
 * copy from https://blog.csdn.net/qq_38366063/article/details/83691424
 *
 * <strong>DataCenterId</strong> generation policy: Modular operations are performed based
 * on the Raft Term and the maximum DataCenterId information
 *
 * <strong>WorkerId</strong> generation policy: Calculate the InetAddress hashcode
 *
 * The repeat rate of the dataCenterId, the value of the maximum dataCenterId times the
 * time of each Raft election. The time for raft to select the master is generally measured
 * in seconds. If the interval of an election is 5 seconds, it will take 150 seconds for
 * the DataCenterId to be repeated. This is still based on the situation that the new master
 * needs to be selected after each election of the Leader
 *
 * @author <a href="mailto:liaochunyhm@live.com">liaochuntao</a>
 */
public class SnakeFlowerIdGenerator implements IdGenerator {

	/**
	 * Start time intercept (2018-08-05 08:34)
	 */
	private static final long TWEPOCH = 1533429269000L;
	private static final long WORKER_ID_BITS = 5L;
	private static final long DATA_CENTER_ID_BITS = 5L;
	public static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    public static final long MAX_DATA_CENTER_ID = ~(-1L << DATA_CENTER_ID_BITS);

	private static final long SEQUENCE_BITS = 12L;
	private static final long SEQUENCE_BITS1 = SEQUENCE_BITS;
	private static final long DATA_CENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
	private static final long TIMESTAMP_LEFT_SHIFT =
			SEQUENCE_BITS + WORKER_ID_BITS + DATA_CENTER_ID_BITS;
	private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private static long workerId;
    private static volatile long dataCenterId;

	private volatile long currentId;
	private long sequence = 0L;
	private long lastTimestamp = -1L;

    public static void setDataCenterId(long dataCenterId) {
        SnakeFlowerIdGenerator.dataCenterId = dataCenterId;
    }

    static {
        InetAddress address;
        try {
            address = InetAddress.getLocalHost();
        }
        catch (final UnknownHostException e) {
            throw new IllegalStateException(
                    "Cannot get LocalHost InetAddress, please check your network!");
        }
        byte[] ipAddressByteArray = address.getAddress();
        workerId = (
                ((ipAddressByteArray[ipAddressByteArray.length - 2] & 0B11) << Byte.SIZE)
                        + (ipAddressByteArray[ipAddressByteArray.length - 1] & 0xFF));
    }

	@Override
    public void init() {
        initialize(workerId, dataCenterId);
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
			sequence = (sequence + 1) & SEQUENCE_MASK;
			if (sequence == 0) {
				timestamp = tilNextMillis(lastTimestamp);
			}
		}
		else {
			sequence = 0L;
		}

		lastTimestamp = timestamp;
		currentId = ((timestamp - TWEPOCH) << TIMESTAMP_LEFT_SHIFT) | (dataCenterId
				<< DATA_CENTER_ID_SHIFT) | (workerId << SEQUENCE_BITS1) | sequence;
		return currentId;
	}

	@Override
    public Map<Object, Object> info() {
		Map<Object, Object> info = new HashMap<>(4);
		info.put("currentId", currentId);
		info.put("dataCenterId", dataCenterId);
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
		if (workerId > MAX_WORKER_ID || workerId < 0) {
			throw new IllegalArgumentException(
					String.format("worker Id can't be greater than %d or less than 0",
                            MAX_WORKER_ID));
		}
		if (datacenterId > MAX_DATA_CENTER_ID || datacenterId < 0) {
			throw new IllegalArgumentException(
					String.format("datacenter Id can't be greater than %d or less than 0",
                            MAX_DATA_CENTER_ID));
		}
        SnakeFlowerIdGenerator.workerId = workerId;
        SnakeFlowerIdGenerator.dataCenterId = datacenterId;
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