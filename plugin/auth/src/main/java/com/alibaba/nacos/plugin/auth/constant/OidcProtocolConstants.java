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

package com.alibaba.nacos.plugin.auth.constant;

/**
 * OIDC/OAuth2 protocol-level shared constants.
 *
 * <p>Contains constants shared between server-side and client-side OIDC implementations,
 * including OIDC Discovery fields, HTTP headers, OAuth2 token request/response fields.
 *
 * @author wangzji
 */
public final class OidcProtocolConstants {
    
    private OidcProtocolConstants() {
    }
    
    // ===== OIDC Discovery =====
    
    public static final String WELL_KNOWN_PATH = "/.well-known/openid-configuration";
    
    public static final String DISCOVERY_TOKEN_ENDPOINT = "token_endpoint";
    
    public static final String DISCOVERY_JWKS_URI = "jwks_uri";
    
    public static final String DISCOVERY_AUTHORIZATION_ENDPOINT = "authorization_endpoint";
    
    public static final String DISCOVERY_USERINFO_ENDPOINT = "userinfo_endpoint";
    
    public static final String DISCOVERY_END_SESSION_ENDPOINT = "end_session_endpoint";
    
    // ===== HTTP Headers & Auth =====
    
    public static final String AUTHORIZATION_HEADER = "Authorization";
    
    public static final String BEARER_PREFIX = "Bearer ";
    
    public static final String ACCESS_TOKEN_PARAM = "accessToken";
    
    public static final String AUTH_PLUGIN_TYPE = "oidc";
    
    // ===== HTTP =====
    
    public static final int HTTP_STATUS_OK = 200;
    
    public static final String CONTENT_TYPE_JSON = "application/json";
    
    public static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded";
    
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
    
    public static final int DEFAULT_READ_TIMEOUT_MS = 10000;
    
    // ===== OAuth2 Token Request =====
    
    public static final String GRANT_TYPE = "grant_type";
    
    public static final String GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials";
    
    public static final String PARAM_CLIENT_ID = "client_id";
    
    public static final String PARAM_CLIENT_SECRET = "client_secret";
    
    public static final String PARAM_SCOPE = "scope";
    
    // ===== Token Response =====
    
    public static final String TOKEN_RESPONSE_ACCESS_TOKEN = "access_token";
    
    public static final String TOKEN_RESPONSE_EXPIRES_IN = "expires_in";
    
    public static final String TOKEN_RESPONSE_TOKEN_TYPE = "token_type";
}
