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

package com.alibaba.nacos.api.ability;

import com.alibaba.nacos.api.config.ClientConfigAbility;
import com.alibaba.nacos.api.naming.ClientNamingAbility;

/**
 * abilities of nacos client.
 *
 * @author liuzunfei
 * @version $Id: ClientAbilities.java, v 0.1 2021年01月24日 00:09 AM liuzunfei Exp $
 */
public class ClientAbilities {
    
    private ClientRemoteAbility remoteAbility = new ClientRemoteAbility();
    
    private ClientConfigAbility configAbility = new ClientConfigAbility();
    
    private ClientNamingAbility namingAbility = new ClientNamingAbility();
    
    public ClientRemoteAbility getRemoteAbility() {
        return remoteAbility;
    }
    
    public void setRemoteAbility(ClientRemoteAbility remoteAbility) {
        this.remoteAbility = remoteAbility;
    }
    
    public ClientConfigAbility getConfigAbility() {
        return configAbility;
    }
    
    public void setConfigAbility(ClientConfigAbility configAbility) {
        this.configAbility = configAbility;
    }
    
    public ClientNamingAbility getNamingAbility() {
        return namingAbility;
    }
    
    public void setNamingAbility(ClientNamingAbility namingAbility) {
        this.namingAbility = namingAbility;
    }
}
