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

import java.util.List;

/**
 * ConfigChangeBatchListenResponse.
 *
 * @author liuzunfei
 * @version $Id: ConfigChangeBatchListenResponse.java, v 0.1 2020年07月14日 3:07 PM liuzunfei Exp $
 */
public class ConfigChangeBatchListenResponse extends Response {
    
    List<String> changedGroupKeys;
    
    public ConfigChangeBatchListenResponse() {
    }
    
    /**
     * build sucess response.
     *
     * @return
     */
    public static ConfigChangeBatchListenResponse buildSucessResponse(List<String> changedGroupKeys) {
        ConfigChangeBatchListenResponse response = new ConfigChangeBatchListenResponse();
        response.setChangedGroupKeys(changedGroupKeys);
        response.setResultCode(ResponseCode.SUCCESS.getCode());
        return response;
    }
    
    /**
     * Getter method for property <tt>changedGroupKeys</tt>.
     *
     * @return property value of changedGroupKeys
     */
    public List<String> getChangedGroupKeys() {
        return changedGroupKeys;
    }
    
    /**
     * Setter method for property <tt>changedGroupKeys</tt>.
     *
     * @param changedGroupKeys value to be assigned to property changedGroupKeys
     */
    public void setChangedGroupKeys(List<String> changedGroupKeys) {
        this.changedGroupKeys = changedGroupKeys;
    }
    
    /**
     * build fail response.
     *
     * @param errorMessage errorMessage.
     * @return
     */
    public static ConfigChangeBatchListenResponse buildFailResponse(String errorMessage) {
        ConfigChangeBatchListenResponse response = new ConfigChangeBatchListenResponse();
        response.setResultCode(ResponseCode.FAIL.getCode());
        response.setMessage(errorMessage);
        return response;
    }
    
}
