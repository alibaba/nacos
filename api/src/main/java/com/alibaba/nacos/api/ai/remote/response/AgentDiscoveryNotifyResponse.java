/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.ai.remote.response;

import com.alibaba.nacos.api.remote.response.Response;

/**
 * Client acknowledgement for one RAD Watch hint.
 *
 * @author Nacos
 */
public class AgentDiscoveryNotifyResponse extends Response {
    
    private String watchKey;
    
    private boolean accepted;
    
    @Override
    public boolean isSuccess() {
        return accepted && super.isSuccess();
    }
    
    public String getWatchKey() {
        return watchKey;
    }
    
    public void setWatchKey(String watchKey) {
        this.watchKey = watchKey;
    }
    
    public boolean isAccepted() {
        return accepted;
    }
    
    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }
}
