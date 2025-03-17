/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.exception;

import com.alibaba.nacos.api.exception.NacosException;

/**
 * ConfigAlreadyExistsException.
 *
 * @author Nacos
 */
public class ConfigAlreadyExistsException extends NacosException {
    
    private static final long serialVersionUID = -8247262927932720692L;
    
    ConfigAlreadyExistsException() {
        super();
    }
    
    public ConfigAlreadyExistsException(int errCode, String errMsg) {
        super(errCode, errMsg);
    }
    
    public ConfigAlreadyExistsException(String errMsg) {
        super(NacosException.CONFIG_ALREADY_EXISTS, errMsg);
    }
    
    public ConfigAlreadyExistsException(int errCode, Throwable throwable) {
        super(errCode, throwable);
    }
    
    public ConfigAlreadyExistsException(int errCode, String errMsg, Throwable throwable) {
        super(errCode, errMsg, throwable);
    }
}