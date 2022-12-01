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

package com.alibaba.nacos.plugin.control.connection.response;

/**
 * conection check code.
 *
 * @author shiyiyue
 */
public class ConnectionCheckCode {
    
    /**
     * check pass.
     */
    public static final int PASS_BY_TOTAL = 200;
    
    /**
     * skip.
     */
    public static final int CHECK_SKIP = 100;
    
    /**
     * deny by total over limit.
     */
    public static final int DENY_BY_TOTAL_OVER = 300;
    
    /**
     * pass by monitor type.
     */
    public static final int PASS_BY_MONITOR = 205;
}
