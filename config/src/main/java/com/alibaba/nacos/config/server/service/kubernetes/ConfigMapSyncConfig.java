/*
 *
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.config.server.service.kubernetes;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ConfigMap synchronization configuration.
 *
 * @author wangyixing
 */
@Component
public class ConfigMapSyncConfig {
    
    @Value("${nacos.k8s.configMap.enabled:false}")
    private boolean enabled;
    
    @Value("${nacos.k8s.configMap.responsible:false}")
    private boolean responsible;
    
    @Value("${nacos.k8s.configMap.outSideCluster:false}")
    private boolean outsideCluster;
    
    @Value("${nacos.k8s.configMap.kubeConfig:}")
    private String kubeConfig;
    
    public static final String K8S_GROUP = "K8S_GROUP";
    
    public static final String SRC_USER = "configMap/k8s";
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public boolean isOutsideCluster() {
        return outsideCluster;
    }
    
    public String getKubeConfig() {
        return kubeConfig;
    }
}
