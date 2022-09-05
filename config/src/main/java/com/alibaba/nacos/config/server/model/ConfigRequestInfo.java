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

package com.alibaba.nacos.config.server.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * ConfigRequestInfo.
 * @author dongyafei
 * @date 2022/8/11
 */
public class ConfigRequestInfo implements Serializable {
    
    private static final long serialVersionUID = 326726654448860273L;
    
    private String srcIp;
    
    private String requestIpApp;
    
    private String betaIps;
    
    public ConfigRequestInfo(String srcIp, String requestIpApp, String betaIps) {
        this.srcIp = srcIp;
        this.requestIpApp = requestIpApp;
        this.betaIps = betaIps;
    }
    
    public ConfigRequestInfo() {
    }
    
    public String getSrcIp() {
        return srcIp;
    }
    
    public void setSrcIp(String srcIp) {
        this.srcIp = srcIp;
    }
    
    public String getRequestIpApp() {
        return requestIpApp;
    }
    
    public void setRequestIpApp(String requestIpApp) {
        this.requestIpApp = requestIpApp;
    }
    
    public String getBetaIps() {
        return betaIps;
    }
    
    public void setBetaIps(String betaIps) {
        this.betaIps = betaIps;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ConfigRequestInfo that = (ConfigRequestInfo) o;
        return Objects.equals(srcIp, that.srcIp) && Objects.equals(requestIpApp, that.requestIpApp) && Objects
                .equals(betaIps, that.betaIps);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(srcIp, requestIpApp, betaIps);
    }
    
    @Override
    public String toString() {
        return "ConfigRequestInfoVo{" + "srcIp='" + srcIp + '\'' + ", requestIpApp='" + requestIpApp + '\''
                + ", betaIps='" + betaIps + '\'' + '}';
    }
}
