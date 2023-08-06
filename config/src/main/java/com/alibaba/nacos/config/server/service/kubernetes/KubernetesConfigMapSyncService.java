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

import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.util.CallGeneratorParams;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.Yaml;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

@Service
public class KubernetesConfigMapSyncService {
    
    private static final Gson gson = new Gson();
    
    @Value("${nacos.k8s.configMap.enabled:false}")
    private boolean enabled = true;
    
    @Value("${nacos.k8s.configMap.responsible:true")
    private boolean responsible = false;
    
    private ConfigInfoPersistService configInfoPersistService;
    
    private ApiClient apiClient;
    
    private boolean isRunning;
    
    
    public KubernetesConfigMapSyncService(
            @Qualifier("embeddedConfigInfoPersistServiceImpl") ConfigInfoPersistService configInfoPersistService) {
        this.configInfoPersistService = configInfoPersistService;
        enabled = Boolean.parseBoolean(System.getProperty("nacos.k8s.configMap.enabled"));
        // If sync is turned on, continue execution.
        if (!enabled) {
            Loggers.MAIN.warn("The nacos KubernetesConfigMapSyncService is disabled.");
            return;
        }
        
        // Calculate whether you are responsible.
        // If the current node is responsible, it will continue to execute.
        if (!responsible) {
            Loggers.MAIN.info("The current node is not responsible.");
            return;
        }
        
        try {
            this.apiClient = ClientBuilder.cluster().build();
        } catch (IOException e) {
            Loggers.MAIN.error("The KubernetesApiClient build failed.");
            isRunning = false;
        }
        
        if (enabled) {
            watchConfigMap();
            isRunning = true;
        }
    }
    
    private void watchConfigMap() {
        SharedInformerFactory factory = new SharedInformerFactory(apiClient);
        CoreV1Api coreV1Api = new CoreV1Api();
        SharedIndexInformer<V1ConfigMap> nodeInformer = factory.sharedIndexInformerFor((CallGeneratorParams params) -> {
            return coreV1Api.listConfigMapForAllNamespacesCall(true, null, null, null, null, null,
                    params.resourceVersion, null, params.timeoutSeconds, params.watch, null);
        }, V1ConfigMap.class, V1ConfigMapList.class);
        nodeInformer.addEventHandler(new ResourceEventHandler<V1ConfigMap>() {
            @Override
            public void onAdd(V1ConfigMap obj) {
                if (obj == null || obj.getMetadata() == null || obj.getData() == null) {
                    return;
                }
                Loggers.MAIN.info("Add configMap ");
                ConfigInfo configInfo = configMapToNacosConfigInfo(obj);
                String srcIp = apiClient.getBasePath();
                configInfoPersistService.addConfigInfo(srcIp, "configmap/k8s", configInfo, null);
            }
            
            @Override
            public void onUpdate(V1ConfigMap oldObj, V1ConfigMap newObj) {
                Loggers.MAIN.info(
                        "Update configMap " + oldObj.getMetadata().getName() + " to " + newObj.getMetadata().getName());
                compareConfigMaps(oldObj, newObj);
                ConfigInfo configInfo = configMapToNacosConfigInfo(newObj);
                String srcIp = apiClient.getBasePath();
                configInfoPersistService.updateConfigInfo(configInfo, "configmap/k8s", srcIp, null);
            }
            
            @Override
            public void onDelete(V1ConfigMap obj, boolean deletedFinalStateUnknown) {
                if (obj == null || obj.getMetadata() == null || obj.getData() == null) {
                    return;
                }
                Loggers.MAIN.info("Delete configMap " + obj.getMetadata().getName());
                String dataId = obj.getMetadata().getName();
                String tenant = obj.getMetadata().getNamespace();
                String srcIp = apiClient.getBasePath();
                configInfoPersistService.removeConfigInfo(dataId, "K8S_GROUP", tenant, srcIp, "configmap/k8s");
            }
            
            private String convertToYaml(Object kubernetesResource) {
                return Yaml.dump(kubernetesResource);
            }
        });
        // Synchronize initialization data to config
        // 启动SharedInformerFactory在后台运行事件监听器
        factory.startAllRegisteredInformers();
        
    }
    
    private void compareConfigMaps(V1ConfigMap previousConfigMap, V1ConfigMap currentConfigMap) {
        // 将先前的 ConfigMap 和当前的 ConfigMap 转换成 JSON 字符串
        String previousJson = gson.toJson(previousConfigMap);
        String currentJson = gson.toJson(currentConfigMap);
        
        // 将 JSON 字符串解析成 JsonElement
        JsonElement previousElement = gson.fromJson(previousJson, JsonElement.class);
        JsonElement currentElement = gson.fromJson(currentJson, JsonElement.class);
        
        if (previousElement.isJsonObject() && currentElement.isJsonObject()) {
            JsonObject previousObj = previousElement.getAsJsonObject();
            JsonObject currentObj = currentElement.getAsJsonObject();
            // 比较两个 JsonObject，找出哪些字段发生了变化
            compareJsonObjects(previousObj, currentObj);
        } else {
            Loggers.MAIN.error("Element is not json.");
        }
    }
    
    private void compareJsonObjects(JsonObject previousObj, JsonObject currentObj) {
        for (String key : previousObj.keySet()) {
            if (!currentObj.has(key)) {
                Loggers.MAIN.info("Field " + key + " removed.");
            } else if (!previousObj.get(key).equals(currentObj.get(key))) {
                Loggers.MAIN.info("Field " + key + " changed.");
                Loggers.MAIN.info("Previous value: " + previousObj.get(key));
                Loggers.MAIN.info("Current value: " + currentObj.get(key));
            }
        }
        
        for (String key : currentObj.keySet()) {
            if (!previousObj.has(key)) {
                Loggers.MAIN.info("Field " + key + " added.");
                Loggers.MAIN.info("Current value: " + currentObj.get(key));
            }
        }
    }
    
    private ConfigInfo configMapToNacosConfigInfo(V1ConfigMap configMap) {
        Loggers.MAIN.info("Converting configMap to nacos ConfigInfo...");
        String dataId = configMap.getMetadata().getName();
        String group = "K8S_GROUP";
        String tenant = configMap.getMetadata().getNamespace();
        String appName = null;
        // 将 ConfigMap 的数据转换为 Nacos 的配置内容
        Map<String, String> dataMap = configMap.getData();
        StringBuilder contentBuilder = new StringBuilder();
        for (Map.Entry<String, String> entry : dataMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            // 拼接配置项的格式，例如 key=value\n
            contentBuilder.append(key).append("=").append(value).append("\n");
        }
        String content = contentBuilder.toString();
        return new ConfigInfo(dataId, group, tenant, appName, content);
    }
    
    
    public boolean isRunning() {
        return isRunning;
    }
}
