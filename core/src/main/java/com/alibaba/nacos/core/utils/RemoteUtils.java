/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.utils;

import com.alibaba.nacos.common.utils.NumberUtil;

/**
 * util of remote.
 *
 * @author liuzunfei
 * @version $Id: RemoteUtils.java, v 0.1 2020年11月12日 8:54 PM liuzunfei Exp $
 */
public class RemoteUtils {
    
    public static final float LOADER_FACTOR = 0.1f;
    
    /**
     * Default remote execute times for CPU count of task processors.
     */
    private static final int REMOTE_EXECUTOR_TIMES_OF_PROCESSORS = 1 << 4;
    
    /**
     * Default remote execute queue size: 16384.
     */
    private static final int REMOTE_EXECUTOR_QUEUE_SIZE = 1 << 14;
    
    /**
     * get remote executors thread times of processors,default is 64. see the usage of this method for detail.
     *
     * @return times of processors.
     */
    public static int getRemoteExecutorTimesOfProcessors() {
        String timesString = System.getProperty("remote.executor.times.of.processors");
        if (NumberUtil.isDigits(timesString)) {
            int times = Integer.parseInt(timesString);
            return times > 0 ? times : REMOTE_EXECUTOR_TIMES_OF_PROCESSORS;
        } else {
            return REMOTE_EXECUTOR_TIMES_OF_PROCESSORS;
        }
    }
    
    public static int getRemoteExecutorQueueSize() {
        String queueSizeString = System.getProperty("remote.executor.queue.size");
        if (NumberUtil.isDigits(queueSizeString)) {
            int size = Integer.parseInt(queueSizeString);
            return size > 0 ? size : REMOTE_EXECUTOR_QUEUE_SIZE;
        } else {
            return REMOTE_EXECUTOR_QUEUE_SIZE;
        }
    }
}
