/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.exception.api;

import com.alibaba.nacos.api.model.v2.ErrorCode;

/**
 * Nacos API Exception For 404 Not Found.
 * @author dongyafei
 * @date 2022/7/22
 */
public class NacosApiNotFoundException extends NacosApiException {
    
    /**
     * serialVersionUID.
     */
    private static final long serialVersionUID = 6849178463253886772L;
    
    public NacosApiNotFoundException() {
    }
    
    public NacosApiNotFoundException(ErrorCode errorCode, Throwable throwable, String errDescription) {
        super(errorCode, throwable, errDescription);
    }
    
    public NacosApiNotFoundException(ErrorCode errorCode, String errDescription) {
        super(errorCode, errDescription);
    }
}
