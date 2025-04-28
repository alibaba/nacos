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

package com.alibaba.nacos.plugin.auth.impl.oidc;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.oauth2.sdk.id.State;

/**
 * Transfer state at Authorization Server and Resource Server.
 *
 * @author Roiocam
 */
@SuppressWarnings("checkstyle:abbreviationaswordinname")
public class OIDCState {
    
    private String state;
    
    private String nonce;
    
    private String origin;
    
    public String getState() {
        return state;
    }
    
    public void setState(String state) {
        this.state = state;
    }
    
    public String getNonce() {
        return nonce;
    }
    
    public void setNonce(String nonce) {
        this.nonce = nonce;
    }
    
    public String getOrigin() {
        return origin;
    }
    
    public void setOrigin(String origin) {
        this.origin = origin;
    }
    
    /**
     * Convert to Nimbus state.
     * @return Nimbus state
     */
    public State toState() {
        String json = JacksonUtils.toJson(this);
        String value = Base64URL.encode(json).toString();
        return new State(value);
    }
    
}
