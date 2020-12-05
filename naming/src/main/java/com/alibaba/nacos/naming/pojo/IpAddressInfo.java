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

package com.alibaba.nacos.naming.pojo;

import java.io.Serializable;
import java.util.Map;

/**
 * Ip and address info.
 *
 * @author caogu.wyp
 * @version $Id: IpAddressInfo.java, v 0.1 2018-09-17 上午10:52 caogu.wyp Exp $$
 */
public class IpAddressInfo implements Serializable {
    
    private boolean valid;
    
    private Map<String, String> metadata;
    
    private Integer port;
    
    private String ip;
    
    private Double weight;
    
    private boolean enabled;
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public boolean isValid() {
        return valid;
    }
    
    /**
     * Setter method for property <tt>valid </tt>.
     *
     * @param valid value to be assigned to property valid
     */
    public void setValid(boolean valid) {
        this.valid = valid;
    }
    
    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    /**
     * Setter method for property <tt>metadata </tt>.
     *
     * @param metadata value to be assigned to property metadata
     */
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
    
    public Integer getPort() {
        return port;
    }
    
    /**
     * Setter method for property <tt>port </tt>.
     *
     * @param port value to be assigned to property port
     */
    public void setPort(Integer port) {
        this.port = port;
    }
    
    public String getIp() {
        return ip;
    }
    
    /**
     * Setter method for property <tt>ip </tt>.
     *
     * @param ip value to be assigned to property ip
     */
    public void setIp(String ip) {
        this.ip = ip;
    }
    
    public Double getWeight() {
        return weight;
    }
    
    /**
     * Setter method for property <tt>weight </tt>.
     *
     * @param weight value to be assigned to property weight
     */
    public void setWeight(Double weight) {
        this.weight = weight;
    }
}
