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

package com.alibaba.nacos.k8s.sync;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configurations for k8s integration.
 *
 * @author EmanuelGi
 */
@Component
public class K8sSyncConfig {
    @Value("${nacos.k8s.sync.enabled:false}")
    private boolean enabled = false;
    
    @Value("${nacos.k8s.sync.outsideCluster:false}")
    private boolean outsideCluster = false;
    
    @Value("${nacos.k8s.sync.kubeConfig:}")
    private String kubeConfig;
    
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
