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

package com.alibaba.nacos.api.config.remote.response;

import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ResponseCode;

/**
 * ConfigChangeListenResponse.
 *
 * @author liuzunfei
 * @version $Id: ConfigChangeListenResponse.java, v 0.1 2020年07月14日 3:07 PM liuzunfei Exp $
 */
public class ConfigChangeListenResponse extends Response {
    
    public ConfigChangeListenResponse() {
        super();
    }
    
    @Override
    public String getType() {
        return ConfigResponseTypeConstants.CONFIG_CHANGE;
    }
    
    /**
     * build sucess response.
     *
     * @return
     */
    public static ConfigChangeListenResponse buildSucessResponse() {
        ConfigChangeListenResponse response = new ConfigChangeListenResponse();
        response.setResultCode(ResponseCode.SUCCESS.getCode());
        return response;
    }
    
    /**
     * build fail response.
     *
     * @param errorMessage errorMessage.
     * @return
     */
    public static ConfigChangeListenResponse buildFailResponse(String errorMessage) {
        ConfigChangeListenResponse response = new ConfigChangeListenResponse();
        response.setResultCode(ResponseCode.FAIL.getCode());
        response.setMessage(errorMessage);
        return response;
    }
    
}
