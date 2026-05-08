/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.auth.oidc;

/**
 * Client-specific constants for OIDC client authentication.
 *
 * <p>Protocol-level constants (Discovery fields, OAuth2 parameters, HTTP headers, etc.)
 * are defined in {@link com.alibaba.nacos.plugin.auth.constant.OidcProtocolConstants}.
 *
 * @author wangzji
 */
public final class OidcClientConstants {
    
    private OidcClientConstants() {
    }
    
    // ----- Properties configuration keys (client-specific) -----
    
    /**
     * OIDC Issuer URI, used for OIDC Discovery.
     */
    public static final String PROP_ISSUER_URI = "nacos.client.auth.oidc.issuer-uri";
    
    /**
     * OAuth2 Client ID for Client Credentials Grant.
     */
    public static final String PROP_CLIENT_ID = "nacos.client.auth.oidc.client-id";
    
    /**
     * OAuth2 Client Secret for Client Credentials Grant.
     */
    public static final String PROP_CLIENT_SECRET = "nacos.client.auth.oidc.client-secret";
    
    /**
     * OAuth2 scopes, defaults to "openid".
     */
    public static final String PROP_SCOPE = "nacos.client.auth.oidc.scope";
    
    /**
     * Token endpoint override. If set, OIDC Discovery is skipped.
     */
    public static final String PROP_TOKEN_ENDPOINT = "nacos.client.auth.oidc.token-endpoint";
    
    // ----- Default values -----
    
    public static final String DEFAULT_SCOPE = "openid";
}
