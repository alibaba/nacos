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

package com.alibaba.nacos.core.distributed.raft.jraft.utils;

import com.alibaba.nacos.consistency.Log;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class JLogUtils {

    public static final JLog toJLog(Log log, final String operate) {
        final JLog jLog = new JLog();
        jLog.setSysOperation(operate);
        jLog.setKey(log.getKey());
        jLog.setData(log.getData());
        jLog.setClassName(log.getClassName());
        jLog.setOperation(log.getOperation());
        jLog.setExtendInfo(log.listExtendInfo());
        return jLog;
    }

}
