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

package com.alibaba.nacos.plugin.auth.impl;

import com.alibaba.nacos.api.plugin.ConfigItemDefinition;
import com.alibaba.nacos.api.plugin.ConfigItemEffectMode;
import com.alibaba.nacos.api.plugin.ConfigItemType;
import com.alibaba.nacos.api.plugin.PluginConfigSpec;
import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.plugin.auth.impl.configuration.NacosAuthPluginConfig;
import com.alibaba.nacos.plugin.auth.impl.configuration.NacosAuthPluginConfigProvider;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.token.TokenManagerDelegate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Nacos default auth plugin service implementation.
 *
 * @author xiweng.yy
 */
public class NacosAuthPluginService extends AbstractNacosAuthPluginService
    implements PluginConfigSpec, NacosAuthPluginConfigProvider {
    
    private static final List<ConfigItemDefinition> CONFIG_DEFINITIONS =
        buildConfigDefinitions();
    
    private final TokenManagerDelegate tokenManagerDelegate;
    
    private volatile NacosAuthPluginConfig config = NacosAuthPluginConfig.defaults();
    
    private volatile AnonymousAccessInitializer anonymousAccessInitializer;
    
    public NacosAuthPluginService() {
        tokenManagerDelegate = new TokenManagerDelegate(this);
    }
    
    private static List<ConfigItemDefinition> buildConfigDefinitions() {
        ConfigItemDefinition secret = new ConfigItemDefinition.Builder(
            NacosAuthPluginConfig.TOKEN_SECRET_KEY, "Token secret key", ConfigItemType.STRING)
            .description("Base64-encoded key used to sign Nacos authentication tokens")
            .defaultValue(AuthConstants.DEFAULT_TOKEN_SECRET_KEY)
            .aliases(Collections.singletonList(AuthConstants.TOKEN_SECRET_KEY)).sensitive(true)
            .effectMode(ConfigItemEffectMode.RESTART).build();
        ConfigItemDefinition expiration = new ConfigItemDefinition.Builder(
            NacosAuthPluginConfig.TOKEN_EXPIRE_SECONDS, "Token expiration", ConfigItemType.NUMBER)
            .description("Token validity period in seconds")
            .defaultValue(AuthConstants.DEFAULT_TOKEN_EXPIRE_SECONDS.toString())
            .aliases(Collections.singletonList(AuthConstants.TOKEN_EXPIRE_SECONDS))
            .effectMode(ConfigItemEffectMode.RUNTIME).build();
        ConfigItemDefinition tokenCache = new ConfigItemDefinition.Builder(
            NacosAuthPluginConfig.TOKEN_CACHE_ENABLE, "Token cache", ConfigItemType.BOOLEAN)
            .description("Cache issued and parsed authentication tokens")
            .defaultValue(Boolean.FALSE.toString())
            .aliases(Collections.singletonList(AuthConstants.TOKEN_CACHE_ENABLE))
            .effectMode(ConfigItemEffectMode.RUNTIME).build();
        ConfigItemDefinition authCache = new ConfigItemDefinition.Builder(
            NacosAuthPluginConfig.CACHING_ENABLED, "Authorization cache", ConfigItemType.BOOLEAN)
            .description("Cache users, roles and permissions")
            .defaultValue(Boolean.TRUE.toString())
            .aliases(Collections.singletonList(AuthConstants.NACOS_CORE_AUTH_CACHING_ENABLED))
            .effectMode(ConfigItemEffectMode.RUNTIME).build();
        ConfigItemDefinition anonymous = new ConfigItemDefinition.Builder(
            NacosAuthPluginConfig.ANONYMOUS_AI_ENABLED, "Anonymous AI access",
            ConfigItemType.BOOLEAN)
            .description("Allow anonymous access to explicitly opted-in AI endpoints")
            .defaultValue(Boolean.FALSE.toString()).aliases(Collections.singletonList(
                AuthConstants.NACOS_CORE_AUTH_NACOS_ANONYMOUS_AI_ENABLED))
            .effectMode(ConfigItemEffectMode.RUNTIME).build();
        return Collections.unmodifiableList(
            Arrays.asList(secret, expiration, tokenCache, authCache, anonymous));
    }
    
    @Override
    public String getAuthServiceName() {
        return AuthConstants.AUTH_PLUGIN_TYPE;
    }
    
    @Override
    public List<ConfigItemDefinition> getConfigDefinitions() {
        return CONFIG_DEFINITIONS;
    }
    
    @Override
    public synchronized void applyConfig(Map<String, String> effectiveConfig) {
        NacosAuthPluginConfig previous = config;
        NacosAuthPluginConfig next = NacosAuthPluginConfig.from(effectiveConfig,
            NacosAuthConfigHolder.getInstance().isAnyAuthEnabled());
        config = next;
        try {
            tokenManagerDelegate.applyTokenConfig();
        } catch (RuntimeException e) {
            config = previous;
            throw e;
        }
        if (next.isAnonymousAiEnabled()) {
            requestAnonymousAccessReconcile();
        }
    }
    
    private void requestAnonymousAccessReconcile() {
        AnonymousAccessInitializer initializer = anonymousAccessInitializer;
        if (initializer != null) {
            initializer.requestReconcile();
        }
    }
    
    @Override
    public Map<String, String> getCurrentConfig() {
        return config.toMap();
    }
    
    @Override
    public NacosAuthPluginConfig getConfig() {
        return config;
    }
    
    @Override
    protected boolean isAnonymousAccessEnabled() {
        return config.isAnonymousAiEnabled();
    }
    
    /**
     * Get the stable token manager facade injected into Spring-managed auth services.
     *
     * @return token manager delegate
     */
    public TokenManagerDelegate getTokenManagerDelegate() {
        return tokenManagerDelegate;
    }
    
    /**
     * Attach the persistence-backed anonymous identity reconciler.
     *
     * @param initializer anonymous access initializer
     */
    public void setAnonymousAccessInitializer(AnonymousAccessInitializer initializer) {
        this.anonymousAccessInitializer = initializer;
        if (config.isAnonymousAiEnabled()) {
            initializer.requestReconcile();
        }
    }
}
