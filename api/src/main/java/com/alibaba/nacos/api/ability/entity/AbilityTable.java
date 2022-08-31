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

package com.alibaba.nacos.api.ability.entity;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.register.AbilityBitOperate;

import java.util.Map;

/**.
 * @author Daydreamer
 * @description This table is linked to a server node or client node.
 * @date 2022/7/12 19:25
 **/
public class AbilityTable implements Cloneable {

    /**.
     * id in connection instance
     */
    private String connectionId;

    /**.
     * ability table
     * key: name from {@link com.alibaba.nacos.api.ability.constant.AbilityKey}
     * value: whether to turn on
     */
    private Map<AbilityKey, Boolean> ability;

    /**.
     * whether it from a server node
     */
    private boolean isServer;
    
    /**.
     * version of the client corresponding to the connection
     */
    private String version;
    
    public AbilityTable() {
    }

    public boolean isServer() {
        return isServer;
    }

    public AbilityTable setServer(boolean server) {
        isServer = server;
        return this;
    }
    
    public String getVersion() {
        return version;
    }
    
    public AbilityTable setVersion(String version) {
        this.version = version;
        return this;
    }
    
    public AbilityTable(String connectionId, Map<AbilityKey, Boolean> ability, boolean isServer, String version) {
        this.connectionId = connectionId;
        this.ability = ability;
        this.isServer = isServer;
        this.version = version;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public AbilityTable setConnectionId(String connectionId) {
        this.connectionId = connectionId;
        return this;
    }

    public Map<AbilityKey, Boolean> getAbility() {
        return ability;
    }

    public AbilityTable setAbility(Map<AbilityKey, Boolean> ability) {
        this.ability = ability;
        return this;
    }
    
    @Override
    public String toString() {
        return "AbilityTable{" + "connectionId='" + connectionId + '\'' + ", ability=" + ability + ", isServer="
                + isServer + ", version='" + version + '\'' + '}';
    }
}
