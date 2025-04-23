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

import com.alibaba.nacos.sys.env.EnvUtil;

/**
 * Open ID Connect Configuration Helper Class.
 *
 * @author Roiocam
 */
@SuppressWarnings("checkstyle:abbreviationaswordinname")
public class OIDCConfigs {
    
    private static final String PREFIX = "nacos.core.auth.oidc";
    
    private static final String KEY_PLACEHOLDER = ".{}";
    
    private static final String NAME = PREFIX + KEY_PLACEHOLDER + ".name";
    
    private static final String SCOPE = PREFIX + KEY_PLACEHOLDER + ".scope";
    
    private static final String CLIENT_ID = PREFIX + KEY_PLACEHOLDER + ".client-id";
    
    private static final String CLIENT_SECRET = PREFIX + KEY_PLACEHOLDER + ".client-secret";
    
    private static final String ISSUER_URI = PREFIX + KEY_PLACEHOLDER + ".issuer-uri";
    
    private static final String ID_TOKEN_SIGN_ALGORITHM = PREFIX + KEY_PLACEHOLDER + ".id-token-sign-algorithm";
    
    public static OIDCConfig getConfiguration(String key) {
        OIDCConfig oidcConfig = new OIDCConfig();
        oidcConfig.setScope(getValueByKey(SCOPE, key));
        oidcConfig.setClientId(getValueByKey(CLIENT_ID, key));
        oidcConfig.setClientSecret(getValueByKey(CLIENT_SECRET, key));
        oidcConfig.setIssuerUri(getValueByKey(ISSUER_URI, key));
        oidcConfig.setIdTokenSignAlgorithm(getValueByKey(ID_TOKEN_SIGN_ALGORITHM, key));
        return oidcConfig;
    }
    
    public static String getValueByKey(String path, String key) {
        return EnvUtil.getProperty(path.replace(KEY_PLACEHOLDER, "." + key));
    }
    
    public static String getProvider() {
        String providerStr = EnvUtil.getProperty(PREFIX, String.class);
        String[] split = providerStr.trim().split(",");
        if (split.length != 1) {
            throw new IllegalArgumentException("OIDC provider only support one provider");
        }
        return split[0];
    }
    
    public static String getNameByKey(String key) {
        return getValueByKey(NAME, key);
    }
}
