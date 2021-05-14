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

package com.alibaba.nacos.naming.constants;

/**
 * Push constants keys and values.
 *
 * @author xiweng.yy
 */
public class PushConstants {
    
    /**
     * Naming push task delay time, unit: milliseconds.
     */
    public static final String PUSH_TASK_DELAY = "nacos.naming.push.pushTaskDelay";
    
    public static final long DEFAULT_PUSH_TASK_DELAY = 500L;
    
    /**
     * Naming push task execute timeout, unit: milliseconds.
     */
    public static final String PUSH_TASK_TIMEOUT = "nacos.naming.push.pushTaskTimeout";
    
    public static final long DEFAULT_PUSH_TASK_TIMEOUT = 5000L;
    
    /**
     * Naming push task retry delay, unit: milliseconds.
     */
    public static final String PUSH_TASK_RETRY_DELAY = "nacos.naming.push.pushTaskRetryDelay";
    
    public static final long DEFAULT_PUSH_TASK_RETRY_DELAY = 1000L;
}
