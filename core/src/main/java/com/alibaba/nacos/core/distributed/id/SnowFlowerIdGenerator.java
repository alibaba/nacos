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
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.InetUtils;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * copy from http://www.cluozy.com/home/hexo/2018/08/11/shariding-JDBC-snowflake/.
 *
 * <strong>WorkerId</strong> generation policy: Calculate the InetAddress hashcode
 *
 * <p>The repeat rate of the dataCenterId, the value of the maximum dataCenterId times the time of each Raft election.
 * The
 * time for raft to select the master is generally measured in seconds. If the interval of an election is 5 seconds, it
 * will take 150 seconds for the DataCenterId to be repeated. This is still based on the situation that the new master
 * needs to be selected after each election of the Leader
 *
 * @author <a href="mailto:liaochunyhm@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class SnowFlowerIdGenerator implements IdGenerator {
    
    private static final String DATETIME_PATTERN =  "yyyy-MM-dd HH:mm:ss.SSS";
    
    /**
     * Start time intercept (2018-08-05 08:34)
     */
    public static final long EPOCH = 1533429240000L;
    
    private static final Logger logger = LoggerFactory.getLogger(SnowFlowerIdGenerator.class);
    
    // the bits of sequence
    private static final long SEQUENCE_BITS = 12L;
    
    // the bits of workerId
    private static final long WORKER_ID_BITS = 10L;
    
    // the mask of sequence (111111111111B = 4095)
    private static final long SEQUENCE_MASK = 4095L;
    
    // the left shift bits of workerId equals 12 bits
    private static final long WORKER_ID_LEFT_SHIFT_BITS = 12L;
    
    // the left shift bits of timestamp equals 22 bits (WORKER_ID_LEFT_SHIFT_BITS + workerId)
    private static final long TIMESTAMP_LEFT_SHIFT_BITS = 22L;
    
    // the max of worker ID is 1024
    private static final long WORKER_ID_MAX_VALUE = 1024L;
    
    private long workerId;
    
    private long sequence;
    
    private long lastTime;
    
    private long currentId;
    
    {
        long workerId = EnvUtil.getProperty("nacos.core.snowflake.worker-id", Integer.class, -1);
        
        if (workerId != -1) {
            this.workerId = workerId;
        } else {
            InetAddress address;
            try {
                address = InetAddress.getByName(InetUtils.getSelfIP());
            } catch (final UnknownHostException e) {
                throw new IllegalStateException("Cannot get LocalHost InetAddress, please check your network!", e);
            }
            byte[] ipAddressByteArray = address.getAddress();
            this.workerId = (((ipAddressByteArray[ipAddressByteArray.length - 2] & 0B11) << Byte.SIZE) + (
                    ipAddressByteArray[ipAddressByteArray.length - 1] & 0xFF));
        }
    }
    
    @Override
    public void init() {
        initialize(workerId);
    }
    
    @Override
    public long currentId() {
        return currentId;
    }
    
    @Override
    public synchronized long nextId() {
        long currentMillis = System.currentTimeMillis();
        Preconditions.checkState(this.lastTime <= currentMillis,
                "Clock is moving backwards, last time is %d milliseconds, current time is %d milliseconds",
                new Object[] {this.lastTime, currentMillis});
        if (this.lastTime == currentMillis) {
            if (0L == (this.sequence = ++this.sequence & 4095L)) {
                currentMillis = this.waitUntilNextTime(currentMillis);
            }
        } else {
            this.sequence = 0L;
        }
        
        this.lastTime = currentMillis;
        logger.debug("{}-{}-{}", (new SimpleDateFormat(DATETIME_PATTERN)).format(new Date(this.lastTime)),
                workerId, this.sequence);
        
        currentId = currentMillis - EPOCH << 22 | workerId << 12 | this.sequence;
        return currentId;
    }
    
    @Override
    public Map<Object, Object> info() {
        Map<Object, Object> info = new HashMap<>(4);
        info.put("currentId", currentId);
        info.put("workerId", workerId);
        return info;
    }
    
    // ==============================Constructors=====================================
    
    /**
     * init
     *
     * @param workerId worker id (0~1024)
     */
    public void initialize(long workerId) {
        if (workerId > WORKER_ID_MAX_VALUE || workerId < 0) {
            throw new IllegalArgumentException(
                    String.format("worker Id can't be greater than %d or less than 0, current workId %d",
                            WORKER_ID_MAX_VALUE, workerId));
        }
        this.workerId = workerId;
    }
    
    /**
     * Block to the next millisecond until a new timestamp is obtained
     *
     * @param lastTimestamp The time intercept of the last ID generated
     * @return Current timestamp
     */
    private long waitUntilNextTime(long lastTimestamp) {
        long time;
        time = System.currentTimeMillis();
        while (time <= lastTimestamp) {
            ;
            time = System.currentTimeMillis();
        }
        
        return time;
    }
    
}
