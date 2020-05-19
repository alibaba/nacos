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

package com.alibaba.nacos.core.utils;

import com.alibaba.nacos.common.utils.Pair;
import org.slf4j.Logger;

import java.util.concurrent.Callable;

/**
 * Simple task time calculation
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class TimerContext {

    private static final ThreadLocal<Pair<String, Long>> TIME_RECORD = new ThreadLocal<>();

    public static void start(String name) {
        long startTime = System.currentTimeMillis();
        TIME_RECORD.set(Pair.with(name, startTime));
    }

    public static void end(final Logger logger) {
        long endTime = System.currentTimeMillis();
        Pair<String, Long> record = TIME_RECORD.get();
        logger.info("{} cost time : {} ms", record.getFirst(), (endTime - record.getSecond()));
        TIME_RECORD.remove();
    }

    public static void run(final Runnable job, final String name, final Logger logger) {
        start(name);
        try {
            job.run();
        } finally {
            end(logger);
        }
    }

    public static <V> V run(final Callable<V> job, final String name, final Logger logger) throws Exception {
        start(name);
        try {
            return job.call();
        } finally {
            end(logger);
        }
    }

}
