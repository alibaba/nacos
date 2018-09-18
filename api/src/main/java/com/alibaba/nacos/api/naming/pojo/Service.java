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
package com.alibaba.nacos.api.naming.pojo;

import java.util.HashMap;
import java.util.Map;

/**
 * @author dungu.zpf
 */
public class Service {

    /**
     * Service name
     */
    private String name;

    /**
     * Protect threshold
     */
    private float protectThreshold = 0.0F;

    /**
     * Application name of this service
     */
    private String app;

    /**
     * Service group which is meant to classify services into different sets.
     */
    private String group;

    /**
     * Health check mode.
     */
    private String healthCheckMode;

    public Service(String name) {
        this.name = name;
    }

    private Map<String, String> metadata = new HashMap<String, String>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHealthCheckMode() {
        return healthCheckMode;
    }

    public void setHealthCheckMode(String healthCheckMode) {
        this.healthCheckMode = healthCheckMode;
    }

    public float getProtectThreshold() {
        return protectThreshold;
    }

    public void setProtectThreshold(float protectThreshold) {
        this.protectThreshold = protectThreshold;
    }

    public String getApp() {
        return app;
    }

    public void setApp(String app) {
        this.app = app;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public void addMetadata(String key, String value) {
        this.metadata.put(key, value);
    }
}
