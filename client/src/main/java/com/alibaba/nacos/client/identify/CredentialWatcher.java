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

import java.io.*;
import java.net.URL;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Credential Watcher
 *
 * @author Nacos
 */
public class CredentialWatcher {
    private static final Logger SpasLogger = LogUtils.logger(CredentialWatcher.class);
    private static final long REFRESH_INTERVAL = 10 * 1000;

    private CredentialService serviceInstance;
    private String appName;
    private String propertyPath;
    private TimerTask watcher;
    private boolean stopped;

    @SuppressWarnings("PMD.AvoidUseTimerRule")
    public CredentialWatcher(String appName, CredentialService serviceInstance) {
        this.appName = appName;
        this.serviceInstance = serviceInstance;
        loadCredential(true);
        watcher = new TimerTask() {
            private Timer timer = new Timer(true);
            private long modified = 0;

            {
                timer.schedule(this, REFRESH_INTERVAL, REFRESH_INTERVAL);
            }

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
        };
    }

    public void stop() {
        if (stopped) {
            return;
        }
        if (watcher != null) {
            synchronized (watcher) {
                watcher.cancel();
                stopped = true;
            }
        }
        SpasLogger.info("[{}] {} is stopped", appName, this.getClass().getSimpleName());
    }

    private void loadCredential(boolean init) {
        boolean logWarn = init;
        if (propertyPath == null) {
            URL url = ClassLoader.getSystemResource(Constants.PROPERTIES_FILENAME);
            if (url != null) {
                propertyPath = url.getPath();
            }
            if (propertyPath == null || propertyPath.isEmpty()) {

                String value = System.getProperty("spas.identity");
                if (StringUtils.isNotEmpty(value)) {
                    propertyPath = value;
                }
                if (propertyPath == null || propertyPath.isEmpty()) {
                    propertyPath = Constants.CREDENTIAL_PATH + (appName == null ? Constants.CREDENTIAL_DEFAULT
                        : appName);
                } else {
                    if (logWarn) {
                        SpasLogger.info("[{}] Defined credential file: -Dspas.identity={}", appName, propertyPath);
                    }
                }
            } else {
                if (logWarn) {
                    SpasLogger.info("[{}] Load credential file from classpath: {}", appName,
                        Constants.PROPERTIES_FILENAME);
                }
            }
        }

        InputStream propertiesIS = null;
        do {
            try {
                propertiesIS = new FileInputStream(propertyPath);
            } catch (FileNotFoundException e) {
                if (appName != null && !appName.equals(Constants.CREDENTIAL_DEFAULT) && propertyPath.equals(
                    Constants.CREDENTIAL_PATH + appName)) {
                    propertyPath = Constants.CREDENTIAL_PATH + Constants.CREDENTIAL_DEFAULT;
                    continue;
                }
                if (!Constants.DOCKER_CREDENTIAL_PATH.equals(propertyPath)) {
                    propertyPath = Constants.DOCKER_CREDENTIAL_PATH;
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
            accessKey = System.getenv(Constants.ENV_ACCESS_KEY);
            secretKey = System.getenv(Constants.ENV_SECRET_KEY);
            if (accessKey == null && secretKey == null) {
                if (logWarn) {
                    SpasLogger.info("{} No credential found", appName);
                }
                return;
            }
        } else {
            Properties properties = new Properties();
            try {
                properties.load(propertiesIS);
            } catch (IOException e) {
                SpasLogger.error("[26] Unable to load credential file, appName:" + appName
                    + "Unable to load credential file " + propertyPath, e);
                propertyPath = null;
                return;
            } finally {
                try {
                    propertiesIS.close();
                } catch (IOException e) {
                    SpasLogger.error("[27] Unable to close credential file, appName:" + appName
                        + "Unable to close credential file " + propertyPath, e);
                }
            }

            if (logWarn) {
                SpasLogger.info("[{}] Load credential file {}", appName, propertyPath);
            }

            if (!Constants.DOCKER_CREDENTIAL_PATH.equals(propertyPath)) {
                if (properties.containsKey(Constants.ACCESS_KEY)) {
                    accessKey = properties.getProperty(Constants.ACCESS_KEY);
                }
                if (properties.containsKey(Constants.SECRET_KEY)) {
                    secretKey = properties.getProperty(Constants.SECRET_KEY);
                }
                if (properties.containsKey(Constants.TENANT_ID)) {
                    tenantId = properties.getProperty(Constants.TENANT_ID);
                }
            } else {
                if (properties.containsKey(Constants.DOCKER_ACCESS_KEY)) {
                    accessKey = properties.getProperty(Constants.DOCKER_ACCESS_KEY);
                }
                if (properties.containsKey(Constants.DOCKER_SECRET_KEY)) {
                    secretKey = properties.getProperty(Constants.DOCKER_SECRET_KEY);
                }

                if (properties.containsKey(Constants.DOCKER_TENANT_ID)) {
                    tenantId = properties.getProperty(Constants.DOCKER_TENANT_ID);
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
            SpasLogger.warn("[1] Credential file missing required property {} Credential file missing {} or {}",
                appName, Constants.ACCESS_KEY, Constants.SECRET_KEY);
            propertyPath = null;
            // return;
        }

        serviceInstance.setCredential(credential);
    }
}
