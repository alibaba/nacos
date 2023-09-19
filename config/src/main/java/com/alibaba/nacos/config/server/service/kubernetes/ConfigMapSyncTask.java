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

import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ConfigMap synchronization task.
 *
 * @author wangyixing
 */
@Component
public class ConfigMapSyncTask {
    
    private CoreV1Api coreV1Api;
    
    private ApiClient apiClient;
    
    @Autowired
    @Qualifier("embeddedConfigInfoPersistServiceImpl")
    private ConfigInfoPersistService configInfoPersistService;
    
    @Autowired
    private ConfigMapSyncConfig configMapSyncConfig;

    @Autowired
    private KubernetesConfigMapSyncServer kubernetesConfigMapSyncServer;
    
    /**
     * Initialize ConfigMapSyncTask.
     * @throws IOException getOutsideApiClient failed
     */
    @PostConstruct
    public void init() throws IOException {
        if (configMapSyncConfig.isOutsideCluster()) {
            apiClient = kubernetesConfigMapSyncServer.getOutsideApiClient();
            coreV1Api = new CoreV1Api(apiClient);
        } else {
            coreV1Api = new CoreV1Api();
            apiClient = coreV1Api.getApiClient();
        }
        OkHttpClient httpClient = apiClient.getHttpClient().newBuilder().readTimeout(Duration.ZERO).build();
        apiClient.setHttpClient(httpClient);
    }
    
    /**
     * Checks and synchronizes configuration maps.
     */
    @Scheduled(fixedDelay = 3600000)
    public void checkAndSyncConfigMaps() {
        try {
            List<V1ConfigMap> configMapList = coreV1Api.listConfigMapForAllNamespaces(null, null, null, null, null,
                    null, null, null, null, null).getItems();
            for (V1ConfigMap configMap : configMapList) {
                String dataId = Objects.requireNonNull(configMap.getMetadata()).getName();
                String group = "K8S_GROUP";
                String namespace = configMap.getMetadata().getNamespace();
                String content = Objects.requireNonNull(configMap.getData()).toString();
                ConfigInfoWrapper configInfo = configInfoPersistService.findConfigInfo(dataId, group, namespace);
                if (configInfo == null) {
                    Loggers.MAIN.info("[{}] find a missed config.", "configMap-sync");
                    kubernetesConfigMapSyncServer.publishConfigMap(configMap, apiClient.getBasePath());
                } else {
                    if (!compareContent(content, configInfo.getContent())) {
                        Loggers.MAIN.info("[{}] find content difference.", "configMap-sync");
                        kubernetesConfigMapSyncServer.publishConfigMap(configMap, apiClient.getBasePath());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Convert and compare configMap and nacos content.
     * @param configMapContent configMap's content
     * @param nacosContent nacos's content
     * @return whether contents are equal.
     */
    private Boolean compareContent(String configMapContent, String nacosContent) {
        configMapContent = configMapContent.substring(1, configMapContent.length() - 1);
        Map<String, String> configMapPair = contentToMap(configMapContent);
        nacosContent = nacosContent.replace("\n", ", ");
        Map<String, String> nacosPair = contentToMap(nacosContent);
        return configMapPair.equals(nacosPair);
    }
    
    private Map<String, String> contentToMap(String content) {
        String[] pairs = content.split(", ");
        Map<String, String> hashMap = new HashMap<>(pairs.length);
        String currentKey = null;
        for (String pair : pairs) {
            if (isPair(pair)) {
                String[] keyValue = pair.replace("\n", "").split("=", 2);
                assert keyValue.length == 2;
                hashMap.put(keyValue[0], keyValue[1]);
                currentKey = keyValue[0];
            } else {
                hashMap.put(currentKey, hashMap.get(currentKey) + pair);
            }
        }
        return hashMap;
    }
    
    private boolean isPair(String line) {
        if (line == null) {
            return false;
        }
        String newLine = line;
        boolean containsDoubleEqual = line.contains("==");
        if (containsDoubleEqual) {
            newLine = line.replace("==", "");
        }
        boolean containsEqual = newLine.contains("=");
        return containsEqual;
    }
}
