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

import org.javatuples.Pair;
import org.slf4j.Logger;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class TimerContext {

    private static final ThreadLocal<Pair<String, Long>> TIME_RECORD = new ThreadLocal<>();

    public static void start(String name) {
        long startTime = System.currentTimeMillis();
        TIME_RECORD.set(Pair.with(name, startTime));
    }

    public static void end(Logger logger) {
        long endTime = System.currentTimeMillis();
        Pair<String, Long> record = TIME_RECORD.get();
        logger.debug("【{}】 cost time : [{}] ms", record.getValue0(), (endTime - record.getValue1()));
        TIME_RECORD.remove();
    }

}
