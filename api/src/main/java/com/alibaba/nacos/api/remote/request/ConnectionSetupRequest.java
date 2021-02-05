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

package com.alibaba.nacos.api.remote.request;

import com.alibaba.nacos.api.ability.ClientAbilities;

import java.util.HashMap;
import java.util.Map;

/**
 * request to setup a connection.
 *
 * @author liuzunfei
 * @version $Id: ConnectionSetupRequest.java, v 0.1 2020年08月06日 2:42 PM liuzunfei Exp $
 */
public class ConnectionSetupRequest extends InternalRequest {
    
    private String clientVersion;
    
    private ClientAbilities abilities;
    
    private String tenant;
    
    private Map<String, String> labels = new HashMap<String, String>();
    
    public ConnectionSetupRequest() {
    }
    
    public String getClientVersion() {
        return clientVersion;
    }
    
    public void setClientVersion(String clientVersion) {
        this.clientVersion = clientVersion;
    }
    
    public Map<String, String> getLabels() {
        return labels;
    }
    
    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }
    
    public String getTenant() {
        return tenant;
    }
    
    public void setTenant(String tenant) {
        this.tenant = tenant;
    }
    
    public ClientAbilities getAbilities() {
        return abilities;
    }
    
    public void setAbilities(ClientAbilities abilities) {
        this.abilities = abilities;
    }
}
