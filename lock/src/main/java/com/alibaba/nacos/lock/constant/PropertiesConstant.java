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

package com.alibaba.nacos.lock.constant;

/**
 * properties constant.
 *
 * @author 985492783@qq.com
 * @date 2023/8/25 0:50
 */
public class PropertiesConstant {
    
    public static final String DEFAULT_AUTO_EXPIRE = "nacos.lock.default_expire_time";
    
    public static final String MAX_AUTO_EXPIRE = "nacos.lock.max_expire_time";
    
    public static final Long DEFAULT_AUTO_EXPIRE_TIME = 30_000L;
    
    public static final Long MAX_AUTO_EXPIRE_TIME = 1800_000L;
}
