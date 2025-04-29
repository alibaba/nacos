/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.lock.constant;

/**
 * lock properties constants.
 * @author 985492783@qq.com
 * @description PropertyConstants
 * @date 2023/6/28 17:38
 */
public class PropertyConstants {
    public static final String LOCK_REQUEST_TIMEOUT = "lockRequestTimeout";
    
    public static final String LOCK_DEFAULT_WAIT_TIME = "nacos.lock.default_wait_time";

    public static final Long LOCK_DEFAULT_WAIT_SECOND = 10_000L;
}
