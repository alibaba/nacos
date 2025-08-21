/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.model.response;

import com.alibaba.nacos.api.ability.ServerAbilities;
import com.alibaba.nacos.api.common.NodeState;
import com.alibaba.nacos.api.utils.StringUtils;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Nacos server member information.
 *
 * @author xiweng.yy
 */
public class NacosMember implements Serializable {
    
    private static final long serialVersionUID = 6295022126554026016L;
    
    private String ip;
    
    private int port = -1;
    
    private volatile NodeState state = NodeState.UP;
    
    private Map<String, Object> extendInfo = Collections.synchronizedMap(new TreeMap<>());
    
    private String address = "";
    
    @Deprecated
    private ServerAbilities abilities = new ServerAbilities();
    
    public String getIp() {
        return ip;
    }
    
    public void setIp(String ip) {
        this.ip = ip;
        this.address = ip + ":" + port;
    }
    
    public int getPort() {
        return port;
    }
    
    public void setPort(int port) {
        this.port = port;
        this.address = ip + ":" + port;
    }
    
    public NodeState getState() {
        return state;
    }
    
    public void setState(NodeState state) {
        this.state = state;
    }
    
    public Map<String, Object> getExtendInfo() {
        return extendInfo;
    }
    
    public void setExtendInfo(Map<String, Object> extendInfo) {
        this.extendInfo = extendInfo;
    }
    
    public String getAddress() {
        return address;
    }
    
    public void setAddress(String address) {
        this.address = address;
    }
    
    public ServerAbilities getAbilities() {
        return abilities;
    }
    
    public void setAbilities(ServerAbilities abilities) {
        this.abilities = abilities;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NacosMember that = (NacosMember) o;
        return port == that.port && StringUtils.equals(ip, that.ip);
    }
    
    @Override
    public String toString() {
        return "Member{" + "ip='" + ip + '\'' + ", port=" + port + ", state=" + state + ", extendInfo=" + extendInfo
                + '}';
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(ip, port);
    }
}
