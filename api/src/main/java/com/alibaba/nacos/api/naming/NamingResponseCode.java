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

package com.alibaba.nacos.api.naming;

import com.alibaba.nacos.api.common.ResponseCode;

/**
 * Business response code of naming module
 *
 * <p>Every code stays between 20001 to 29999.
 *
 * @author nkorange
 * @author 1.2.0
 */
public class NamingResponseCode extends ResponseCode {
    
    /**
     * The requested resource is not found.
     */
    public static final int RESOURCE_NOT_FOUND = 20404;
    
    /**
     * Stop or no need to retry.
     */
    public static final int NO_NEED_RETRY = 21600;
    
}
