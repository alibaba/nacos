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

package com.alibaba.nacos.plugin.config.model;

import java.util.Map;

/**
 * every plugin service when handle finish ,will produce a result report.
 *
 * @author liyunfei
 */
public class ConfigChangeHandleReport {
    
    private String pointType;
    
    /**
     * when execute pjp,will produce result,if dont execute retVal == null.
     */
    private Object retVal;
    
    private String msg;
    
    private Map<String, Object> additionInfo;
    
    public ConfigChangeHandleReport(String pointType) {
        this.pointType = pointType;
    }
    
    public String getPointType() {
        return pointType;
    }
    
    public void setPointType(String pointType) {
        this.pointType = pointType;
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
    
    public Map<String, Object> getAdditionInfo() {
        return additionInfo;
    }
    
    public void setAdditionInfo(Map<String, Object> additionInfo) {
        this.additionInfo = additionInfo;
    }
}
