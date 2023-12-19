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

package com.alibaba.nacos.api.remote.request;

import java.util.Map;

import static com.alibaba.nacos.api.common.Constants.Remote.INTERNAL_MODULE;

/**
 * Server tells the client that the connection is established.
 *
 * @author Daydreamer.
 * @date 2022/7/12 19:21
 **/
public class SetupAckRequest extends ServerRequest {
    
    private Map<String, Boolean> abilityTable;
    
    public SetupAckRequest() {
    }
    
    public SetupAckRequest(Map<String, Boolean> abilityTable) {
        this.abilityTable = abilityTable;
    }
    
    public Map<String, Boolean> getAbilityTable() {
        return abilityTable;
    }
    
    public void setAbilityTable(Map<String, Boolean> abilityTable) {
        this.abilityTable = abilityTable;
    }
    
    @Override
    public String getModule() {
        return INTERNAL_MODULE;
    }
}
