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

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.util.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ConfigMapSyncTask {
    
    private final CoreV1Api coreV1Api;
    
    private final ApiClient apiClient;
    
    private final ConfigService nacosConfigService;
    
    @Autowired
    public KubernetesConfigMapSyncServer kubernetesConfigMapSyncServer;
    
    @Autowired
    public ConfigInfoPersistService configInfoPersistService;
    
    
    public ConfigMapSyncTask() throws IOException, NacosException {
        this.apiClient = Config.defaultClient();
        Configuration.setDefaultApiClient(apiClient);
        coreV1Api = new CoreV1Api();
        
        nacosConfigService = NacosFactory.createConfigService("TODO");
    }
    
    // 每小时从k8s中list所有的configmaps, 如果nacos中没有则添加
    @Scheduled(fixedDelay = 3600000) // 每小时对帐
    public void checkAndSyncConfigMaps() {
        try {
            V1ConfigMapList configMapList = (V1ConfigMapList) coreV1Api.listConfigMapForAllNamespacesCall(null, null,
                    null, null, null, null, null, null, null, null, null);
            for (V1ConfigMap configMap : configMapList.getItems()) {
                String dataId = configMap.getMetadata().getName();
                String group = "K8S_GROUP";
                String content = configMap.getData().toString();
                
                if (nacosConfigService.getConfig(dataId, group, 1000).isEmpty()) {
                    Loggers.MAIN.info("find config missed");
                    ConfigInfo configInfo = kubernetesConfigMapSyncServer.configMapToNacosConfigInfo(configMap);
                    configInfoPersistService.updateConfigInfo(configInfo, "configmap/k8s", apiClient.getBasePath(), null);
                    nacosConfigService.publishConfig(dataId, group, content);
                    //                    coreV1Api.createNamespacedConfigMap("default", configMap, null, null, null);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
    }
}
