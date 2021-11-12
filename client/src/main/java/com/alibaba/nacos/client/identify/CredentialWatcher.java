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
import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Credential Watcher.
 *
 * @author Nacos
 */
public class CredentialWatcher {
    
    private static final Logger SPAS_LOGGER = LogUtils.logger(CredentialWatcher.class);
    
    private static final long REFRESH_INTERVAL = 10 * 1000L;
    
    private static final String ACCESS_KEY = "accessKey";
    
    private static final String SECRET_KEY = "secretKey";
    
    private static final String TENANT_ID = "tenantId";
    
    private static final String PROPERTIES_FILENAME = "spas.properties";
    
    private static final String CREDENTIAL_PATH = "/home/admin/.spas_key/";
    
    private static final String CREDENTIAL_DEFAULT = "default";
    
    private static final String DOCKER_CREDENTIAL_PATH = "/etc/instanceInfo";
    
    private static final String DOCKER_ACCESS_KEY = "env_spas_accessKey";
    
    private static final String DOCKER_SECRET_KEY = "env_spas_secretKey";
    
    private static final String DOCKER_TENANT_ID = "ebv_spas_tenantId";
    
    private static final String SPAS_IDENTITY = "spas.identity";
    
    private static final String NACOS_CLIENT_IDENTIFY_WATCHER_THREAD_NAME = "com.alibaba.nacos.client.identify.watcher";
    
    private final CredentialService serviceInstance;
    
    private final String appName;
    
    private final ScheduledExecutorService executor;
    
    private String propertyPath;
    
    private boolean stopped;
    
    public CredentialWatcher(String appName, CredentialService serviceInstance) {
        this.appName = appName;
        this.serviceInstance = serviceInstance;
        loadCredential(true);
        
        executor = ExecutorFactory.newSingleScheduledExecutorService(
                new NameThreadFactory(NACOS_CLIENT_IDENTIFY_WATCHER_THREAD_NAME));
        
        executor.scheduleWithFixedDelay(new Runnable() {
            private long modified = 0;
            
            @Override
            public void run() {
                synchronized (this) {
                    if (stopped) {
                        return;
                    }
                    boolean reload = false;
                    if (propertyPath == null) {
                        reload = true;
                    } else {
                        File file = new File(propertyPath);
                        long lastModified = file.lastModified();
                        if (modified != lastModified) {
                            reload = true;
                            modified = lastModified;
                        }
                    }
                    if (reload) {
                        loadCredential(false);
                    }
                }
            }
        }, REFRESH_INTERVAL, REFRESH_INTERVAL, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Stop watcher.
     */
    public void stop() {
        if (stopped) {
            return;
        }
        if (executor != null) {
            synchronized (executor) {
                stopped = true;
                executor.shutdown();
            }
        }
        SPAS_LOGGER.info("[{}] {} is stopped", appName, this.getClass().getSimpleName());
    }
    
    private void loadCredential(boolean init) {
        if (propertyPath == null) {
            URL url = ClassLoader.getSystemResource(PROPERTIES_FILENAME);
            if (url != null) {
                propertyPath = url.getPath();
            }
            if (propertyPath == null || propertyPath.isEmpty()) {
                
                String value = System.getProperty(SPAS_IDENTITY);
                if (StringUtils.isNotEmpty(value)) {
                    propertyPath = value;
                }
                if (propertyPath == null || propertyPath.isEmpty()) {
                    propertyPath = CREDENTIAL_PATH + (appName == null ? CREDENTIAL_DEFAULT : appName);
                } else {
                    if (init) {
                        SPAS_LOGGER.info("[{}] Defined credential file: -Dspas.identity={}", appName, propertyPath);
                    }
                }
            } else {
                if (init) {
                    SPAS_LOGGER.info("[{}] Load credential file from classpath: {}", appName, PROPERTIES_FILENAME);
                }
            }
        }
        
        InputStream propertiesIS = null;
        do {
            try {
                propertiesIS = new FileInputStream(propertyPath);
            } catch (FileNotFoundException e) {
                if (appName != null && !appName.equals(CREDENTIAL_DEFAULT) && propertyPath.equals(
                        CREDENTIAL_PATH + appName)) {
                    propertyPath = CREDENTIAL_PATH + CREDENTIAL_DEFAULT;
                    continue;
                }
                if (!DOCKER_CREDENTIAL_PATH.equals(propertyPath)) {
                    propertyPath = DOCKER_CREDENTIAL_PATH;
                    continue;
                }
            }
            break;
        } while (true);
        
        String accessKey = null;
        String secretKey = null;
        String tenantId = null;
        if (propertiesIS == null) {
            propertyPath = null;
            accessKey = System.getenv(ACCESS_KEY);
            secretKey = System.getenv(SECRET_KEY);
            if (accessKey == null && secretKey == null) {
                if (init) {
                    SPAS_LOGGER.info("{} No credential found", appName);
                }
                return;
            }
        } else {
            Properties properties = new Properties();
            try {
                properties.load(propertiesIS);
            } catch (IOException e) {
                SPAS_LOGGER.error(
                        "[26] Unable to load credential file, appName:" + appName + "Unable to load credential file "
                                + propertyPath, e);
                propertyPath = null;
                return;
            } finally {
                try {
                    propertiesIS.close();
                } catch (IOException e) {
                    SPAS_LOGGER.error("[27] Unable to close credential file, appName:" + appName
                            + "Unable to close credential file " + propertyPath, e);
                }
            }
            
            if (init) {
                SPAS_LOGGER.info("[{}] Load credential file {}", appName, propertyPath);
            }
            
            if (!DOCKER_CREDENTIAL_PATH.equals(propertyPath)) {
                if (properties.containsKey(ACCESS_KEY)) {
                    accessKey = properties.getProperty(ACCESS_KEY);
                }
                if (properties.containsKey(SECRET_KEY)) {
                    secretKey = properties.getProperty(SECRET_KEY);
                }
                if (properties.containsKey(TENANT_ID)) {
                    tenantId = properties.getProperty(TENANT_ID);
                }
            } else {
                if (properties.containsKey(DOCKER_ACCESS_KEY)) {
                    accessKey = properties.getProperty(DOCKER_ACCESS_KEY);
                }
                if (properties.containsKey(DOCKER_SECRET_KEY)) {
                    secretKey = properties.getProperty(DOCKER_SECRET_KEY);
                }
                
                if (properties.containsKey(DOCKER_TENANT_ID)) {
                    tenantId = properties.getProperty(DOCKER_TENANT_ID);
                }
            }
        }
        
        if (accessKey != null) {
            accessKey = accessKey.trim();
        }
        if (secretKey != null) {
            secretKey = secretKey.trim();
        }
        
        if (tenantId != null) {
            tenantId = tenantId.trim();
        }
        
        Credentials credential = new Credentials(accessKey, secretKey, tenantId);
        if (!credential.valid()) {
            SPAS_LOGGER.warn("[1] Credential file missing required property {} Credential file missing {} or {}",
                    appName, ACCESS_KEY, SECRET_KEY);
            propertyPath = null;
            // return;
        }
        
        serviceInstance.setCredential(credential);
    }
}
