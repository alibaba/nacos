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

package com.alibaba.nacos.plugin.config.model;

import com.alibaba.nacos.plugin.config.constants.ConfigChangePointCutTypes;

/**
 * ConfigChangeResponse.
 *
 * @author liyunfei
 */
public class ConfigChangeResponse {

    private ConfigChangePointCutTypes responseType;

    private boolean isSuccess;

    private Object retVal;

    private String msg;
    
    private Object[] args;

    public ConfigChangeResponse(ConfigChangePointCutTypes responseType) {
        this.responseType = responseType;
    }

    public ConfigChangePointCutTypes getResponseType() {
        return responseType;
    }

    public void setResponseType(ConfigChangePointCutTypes responseType) {
        this.responseType = responseType;
    }

    public boolean isSuccess() {
        return isSuccess;
    }

    public void setSuccess(boolean success) {
        isSuccess = success;
    }

    public Object getRetVal() {
        return retVal;
    }

    public void setRetVal(Object retVal) {
        this.retVal = retVal;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
    
    public Object[] getArgs() {
        return args;
    }
    
    public void setArgs(Object[] args) {
        this.args = args;
    }
}
