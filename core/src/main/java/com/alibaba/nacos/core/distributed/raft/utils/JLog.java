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

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.consistency.NLog;

/**
 * JRaft Adapt Log Object
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class JLog extends NLog {

    private JLogOperaton operaton;

    JLog() {
    }

    public JLogOperaton getOperaton() {
        return operaton;
    }

    public void setOperaton(JLogOperaton operaton) {
        this.operaton = operaton;
    }

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }

    public static enum JLogOperaton {

        MODIFY_OPERATION,

        READ_OPERATION

    }
}
