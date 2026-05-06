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

package com.alibaba.nacos.plugin.control.tps.response;

/**
 * tps result code.
 *
 * @author shiyiyue
 */
public class TpsResultCode {
    
    public static final int PASS_BY_POINT = 200;
    
    /**
     * rule denied,but pass by monitor.
     */
    public static final int PASS_BY_MONITOR = 201;
    
    /**
     * deny by point rule.
     */
    public static final int DENY_BY_POINT = 300;
    
    /**
     * skip.
     */
    public static final int CHECK_SKIP = 100;
    
    
}
