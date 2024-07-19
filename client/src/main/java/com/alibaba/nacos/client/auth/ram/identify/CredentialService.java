/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.auth.ram.identify;

import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Credential Service.
 *
 * @author Nacos
 */
public final class CredentialService implements SpasCredentialLoader {
    
    private static final Logger LOGGER = LogUtils.logger(CredentialService.class);
    
    private static final ConcurrentHashMap<String, CredentialService> INSTANCES = new ConcurrentHashMap<>();
    
    private final String appName;
    
    private Credentials credentials = new Credentials();
    
    private final CredentialWatcher watcher;
    
    private CredentialListener listener;
    
    private CredentialService(String appName) {
        if (appName == null) {
            String value = NacosClientProperties.PROTOTYPE.getProperty(IdentifyConstants.PROJECT_NAME_PROPERTY);
            if (StringUtils.isNotEmpty(value)) {
                appName = value;
            }
        }
        this.appName = appName;
        watcher = new CredentialWatcher(appName, this);
    }
    
    public static CredentialService getInstance() {
        return getInstance(null);
    }
    
    public static CredentialService getInstance(String appName) {
        String key = appName != null ? appName : IdentifyConstants.NO_APP_NAME;
        return INSTANCES.computeIfAbsent(key, k -> new CredentialService(appName));
    }
    
    public static CredentialService freeInstance() {
        return freeInstance(null);
    }
    
    /**
     * Free instance.
     *
     * @param appName app name
     * @return {@link CredentialService}
     */
    public static CredentialService freeInstance(String appName) {
        String key = appName != null ? appName : IdentifyConstants.NO_APP_NAME;
        CredentialService instance = INSTANCES.remove(key);
        if (instance != null) {
            instance.free();
        }
        return instance;
    }
    
    /**
     * Free service.
     */
    public void free() {
        if (watcher != null) {
            watcher.stop();
        }
        LOGGER.info("[{}] {} is freed", appName, this.getClass().getSimpleName());
    }
    
    @Override
    public Credentials getCredential() {
        return credentials;
    }
    
    public void setCredential(Credentials credential) {
        boolean changed = !(credentials == credential || (credentials != null && credentials.identical(credential)));
        credentials = credential;
        if (changed && listener != null) {
            listener.onUpdateCredential();
        }
    }
    
    public void setStaticCredential(Credentials credential) {
        if (watcher != null) {
            watcher.stop();
        }
        setCredential(credential);
    }
    
    public void registerCredentialListener(CredentialListener listener) {
        this.listener = listener;
    }
}
