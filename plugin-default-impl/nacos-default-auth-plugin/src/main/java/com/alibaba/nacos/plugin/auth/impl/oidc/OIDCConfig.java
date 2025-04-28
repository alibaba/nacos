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

/**
 * Open ID Connect Configuration.
 *
 * @author Roiocam
 */
@SuppressWarnings("checkstyle:abbreviationaswordinname")
public class OIDCConfig {
    
    private String scope;
    
    private String clientId;
    
    private String clientSecret;
    
    private String issuerUri;
    
    private String idTokenSignAlgorithm;
    
    public String getIdTokenSignAlgorithm() {
        return idTokenSignAlgorithm;
    }
    
    public void setIdTokenSignAlgorithm(String idTokenSignAlgorithm) {
        this.idTokenSignAlgorithm = idTokenSignAlgorithm;
    }
    
    public String getClientSecret() {
        return clientSecret;
    }
    
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }
    
    public String getScope() {
        return scope;
    }
    
    public void setScope(String scope) {
        this.scope = scope;
    }
    
    public String getClientId() {
        return clientId;
    }
    
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
    
    public String getIssuerUri() {
        return issuerUri;
    }
    
    public void setIssuerUri(String issuerUri) {
        this.issuerUri = issuerUri;
    }
    
}
