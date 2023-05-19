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

package com.alibaba.nacos.naming.push.v2;

import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.naming.NamingResponseCode;

/**
 * Exception that does not require retry.
 *
 * @author xiweng.yy
 */
public class NoRequiredRetryException extends NacosRuntimeException {
    
    private static final long serialVersionUID = -7941235764759109405L;
    
    public NoRequiredRetryException() {
        super(NamingResponseCode.NO_NEED_RETRY);
    }
}
