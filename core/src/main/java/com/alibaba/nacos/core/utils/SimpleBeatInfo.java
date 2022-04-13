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

package com.alibaba.nacos.core.utils;

import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;

/**
 * simple beat info.
 *
 * @author zrlw@sina.com
 */
public class SimpleBeatInfo {
    
    private int port;
    
    private String ip;
    
    @Override
    public String toString() {
        return "SimpleBeatInfo{" + "port=" + port + ", ip='" + ip + '}';
    }
    
    public String getIp() {
        return ip;
    }
    
    public void setIp(String ip) {
        this.ip = ip;
    }
    
    public int getPort() {
        return port;
    }
    
    public void setPort(int port) {
        this.port = port;
    }
    
    public static String getBeatId(HttpServletRequest req) {
        String beatId;
        String beat = WebUtils.optional(req, "beat", StringUtils.EMPTY);
        if (StringUtils.isNotBlank(beat)) {
            SimpleBeatInfo simpleBeatInfo = JacksonUtils.toObj(beat, SimpleBeatInfo.class);
            beatId = simpleBeatInfo.getIp() + ":"
                    + simpleBeatInfo.getPort() + " "
                    + WebUtils.required(req, CommonParams.SERVICE_NAME);
        } else {
            beatId = WebUtils.optional(req, "ip", StringUtils.EMPTY) + ":"
                    + WebUtils.optional(req, "port", "0") + " "
                    + WebUtils.required(req, CommonParams.SERVICE_NAME);
        }
        return beatId;
    }
}
