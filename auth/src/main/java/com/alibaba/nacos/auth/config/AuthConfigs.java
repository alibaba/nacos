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

package com.alibaba.nacos.auth.config;

import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.common.JustForTest;
import com.alibaba.nacos.common.event.ServerConfigChangeEvent;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.ConvertUtils;
import com.alibaba.nacos.sys.env.EnvUtil;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.DecodingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Auth related configurations.
 *
 * @author nkorange
 * @author mai.jh
 * @since 1.2.0
 */
@Configuration
public class AuthConfigs extends Subscriber<ServerConfigChangeEvent> {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthConfigs.class);
    
    @JustForTest
    private static Boolean cachingEnabled = null;
    
    /**
     * Whether auth enabled.
     */
    @Value("${" + Constants.Auth.NACOS_CORE_AUTH_ENABLED + ":false}")
    private boolean authEnabled;
    
    /**
     * secret key.
     */
    @Value("${" + Constants.Auth.NACOS_CORE_AUTH_DEFAULT_TOKEN_SECRET_KEY + ":}")
    private String secretKey;
    
    /**
     * secret key byte array.
     */
    private byte[] secretKeyBytes;
    
    /**
     * Token validity time(seconds).
     */
    @Value("${" + Constants.Auth.NACOS_CORE_AUTH_DEFAULT_TOKEN_EXPIRE_SECONDS + ":18000}")
    private long tokenValidityInSeconds;
    
    /**
     * Which auth system is in use.
     */
    @Value("${" + Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE + ":}")
    private String nacosAuthSystemType;
    
    @Value("${" + Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_KEY + ":}")
    private String serverIdentityKey;
    
    @Value("${" + Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_VALUE + ":}")
    private String serverIdentityValue;
    
    @Value("${" + Constants.Auth.NACOS_CORE_AUTH_ENABLE_USER_AGENT_AUTH_WHITE + ":false}")
    private boolean enableUserAgentAuthWhite;
    
    public AuthConfigs() {
        NotifyCenter.registerSubscriber(this);
    }
    
    public byte[] getSecretKeyBytes() {
        if (secretKeyBytes == null) {
            try {
                secretKeyBytes = Decoders.BASE64.decode(secretKey);
            } catch (DecodingException e) {
                secretKeyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
            }
            
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
        return authEnabled;
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
        return ConvertUtils.toBoolean(
                EnvUtil.getProperty(Constants.Auth.NACOS_CORE_AUTH_CACHING_ENABLED, "true"));
    }
    
    @JustForTest
    public static void setCachingEnabled(boolean cachingEnabled) {
        AuthConfigs.cachingEnabled = cachingEnabled;
    }
    
    @Override
    public void onEvent(ServerConfigChangeEvent event) {
        try {
            authEnabled = EnvUtil.getProperty(Constants.Auth.NACOS_CORE_AUTH_ENABLED, Boolean.class, false);
            cachingEnabled = EnvUtil.getProperty(Constants.Auth.NACOS_CORE_AUTH_CACHING_ENABLED, Boolean.class,
                    true);
            serverIdentityKey = EnvUtil.getProperty(Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_KEY, "");
            serverIdentityValue = EnvUtil.getProperty(Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_VALUE, "");
            enableUserAgentAuthWhite = EnvUtil.getProperty(
                    Constants.Auth.NACOS_CORE_AUTH_ENABLE_USER_AGENT_AUTH_WHITE, Boolean.class,
                    false);
            nacosAuthSystemType = EnvUtil.getProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE, "");
        } catch (Exception e) {
            LOGGER.warn("Upgrade auth config from env failed, use old value", e);
        }
    }
    
    @Override
    public Class<? extends Event> subscribeType() {
        return ServerConfigChangeEvent.class;
    }
}
