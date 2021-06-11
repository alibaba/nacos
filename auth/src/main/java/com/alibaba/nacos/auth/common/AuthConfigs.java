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

package com.alibaba.nacos.auth.common;

import com.alibaba.nacos.common.JustForTest;
import com.alibaba.nacos.sys.env.EnvUtil;
import io.jsonwebtoken.io.Decoders;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;

/**
 * Auth related configurations.
 *
 * @author nkorange
 * @author mai.jh
 * @since 1.2.0
 */
@Configuration
public class AuthConfigs {
    
    @JustForTest
    private static Boolean cachingEnabled = null;
    
    /**
     * secret key.
     */
    @Value("${nacos.core.auth.default.token.secret.key:}")
    private String secretKey;
    
    /**
     * secret key byte array.
     */
    private byte[] secretKeyBytes;
    
    /**
     * Token validity time(seconds).
     */
    @Value("${nacos.core.auth.default.token.expire.seconds:18000}")
    private long tokenValidityInSeconds;
    
    /**
     * Which auth system is in use.
     */
    @Value("${nacos.core.auth.system.type:}")
    private String nacosAuthSystemType;
    
    @Value("${nacos.core.auth.server.identity.key:}")
    private String serverIdentityKey;
    
    @Value("${nacos.core.auth.server.identity.value:}")
    private String serverIdentityValue;
    
    @Value("${nacos.core.auth.enable.userAgentAuthWhite:true}")
    private boolean enableUserAgentAuthWhite;
    
    public byte[] getSecretKeyBytes() {
        if (secretKeyBytes == null) {
            secretKeyBytes = Decoders.BASE64.decode(secretKey);
        }
        return secretKeyBytes;
    }
    
    public long getTokenValidityInSeconds() {
        return tokenValidityInSeconds;
    }
    
    public String getNacosAuthSystemType() {
        return nacosAuthSystemType;
    }
    
    public String getServerIdentityKey() {
        return serverIdentityKey;
    }
    
    public String getServerIdentityValue() {
        return serverIdentityValue;
    }
    
    public boolean isEnableUserAgentAuthWhite() {
        return enableUserAgentAuthWhite;
    }
    
    /**
     * auth function is open.
     *
     * @return auth function is open
     */
    public boolean isAuthEnabled() {
        // Runtime -D parameter has higher priority:
        String enabled = System.getProperty("nacos.core.auth.enabled");
        if (StringUtils.isNotBlank(enabled)) {
            return BooleanUtils.toBoolean(enabled);
        }
        return BooleanUtils
                .toBoolean(EnvUtil.getProperty("nacos.core.auth.enabled", "false"));
    }
    
    /**
     * Whether permission information can be cached.
     *
     * @return bool
     */
    public boolean isCachingEnabled() {
        if (Objects.nonNull(AuthConfigs.cachingEnabled)) {
            return cachingEnabled;
        }
        return BooleanUtils
                .toBoolean(EnvUtil.getProperty("nacos.core.auth.caching.enabled", "true"));
    }
    
    @JustForTest
    public static void setCachingEnabled(boolean cachingEnabled) {
        AuthConfigs.cachingEnabled = cachingEnabled;
    }
}
