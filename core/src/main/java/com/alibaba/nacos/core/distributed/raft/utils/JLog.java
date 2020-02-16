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

package com.alibaba.nacos.core.distributed.raft.utils;

import com.alibaba.nacos.consistency.NLog;

/**
 * JRaft Adapt Log Object
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class JLog extends NLog {

    public static final String USER_OPERATION = "user_operation";
    public static final String SYS_OPERATION = "sys_operation";

    private String sysOperation;

    JLog() {}

    public String getSysOperation() {
        return sysOperation;
    }

    public void setSysOperation(String sysOperation) {
        this.sysOperation = sysOperation;
    }
}
