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

package com.alibaba.nacos.core.remote.control;

/**
 * MonitorType.
 *
 * @author liuzunfei
 * @version $Id: MonitorType.java, v 0.1 2021年01月12日 20:38 PM liuzunfei Exp $
 */
public enum MonitorType {
    // monitor mode.
    MONITOR("monitor", "only monitor ,not reject  request."),
    //intercept mode.
    INTERCEPT("intercept", "reject  request if tps over limit");
    
    String type;
    
    String desc;
    
    MonitorType(String type, String desc) {
        this.type = type;
        this.desc = desc;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getDesc() {
        return desc;
    }
    
    public void setDesc(String desc) {
        this.desc = desc;
    }
}
