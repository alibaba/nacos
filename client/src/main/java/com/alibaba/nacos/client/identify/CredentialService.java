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
package com.alibaba.nacos.client.identify;

import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.client.utils.StringUtils;
import org.slf4j.Logger;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Credential Service
 *
 * @author Nacos
 */
public final class CredentialService implements SpasCredentialLoader {
    private static final Logger LOGGER = LogUtils.logger(CredentialService.class);

    private static ConcurrentHashMap<String, CredentialService> instances
        = new ConcurrentHashMap<String, CredentialService>();

    private String appName;
    private Credentials credentials = new Credentials();
    private CredentialWatcher watcher;
    private CredentialListener listener;

    private CredentialService(String appName) {
        if (appName == null) {
            String value = System.getProperty("project.name");
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
        String key = appName != null ? appName : Constants.NO_APP_NAME;
        CredentialService instance = instances.get(key);
        if (instance == null) {
            instance = new CredentialService(appName);
            CredentialService previous = instances.putIfAbsent(key, instance);
            if (previous != null) {
                instance = previous;
            }
        }
        return instance;
    }

    public static CredentialService freeInstance() {
        return freeInstance(null);
    }

    public static CredentialService freeInstance(String appName) {
        String key = appName != null ? appName : Constants.NO_APP_NAME;
        CredentialService instance = instances.remove(key);
        if (instance != null) {
            instance.free();
        }
        return instance;
    }

    public void free() {
        if (watcher != null) {
            watcher.stop();
        }
        LOGGER.info("[{}] {} is freed", appName, this.getClass().getSimpleName());
    }

    @Override
    public Credentials getCredential() {
        Credentials localCredential = credentials;
        if (localCredential.valid()) {
            return localCredential;
        }
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

    @Deprecated
    public void setAccessKey(String accessKey) {
        credentials.setAccessKey(accessKey);
    }

    @Deprecated
    public void setSecretKey(String secretKey) {
        credentials.setSecretKey(secretKey);
    }

    @Deprecated
    public String getAccessKey() {
        return credentials.getAccessKey();
    }

    @Deprecated
    public String getSecretKey() {
        return credentials.getSecretKey();
    }

}
