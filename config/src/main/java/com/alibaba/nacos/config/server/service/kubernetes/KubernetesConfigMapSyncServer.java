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
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.Pair;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import com.alibaba.nacos.plugin.encryption.handler.EncryptionHandler;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.util.CallGeneratorParams;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.Yaml;
import okhttp3.OkHttpClient;
import org.checkerframework.checker.units.qual.C;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

@Service
public class KubernetesConfigMapSyncServer {
    
    private static final Gson gson = new Gson();
    
    private boolean isRunning;
    
    private SharedInformerFactory factory;
    
    @Autowired
    private ConfigMapSyncConfig configMapSyncConfig;
    
    @Autowired
    private ConfigOperationService configOperationService;
    
    @Autowired
    @Qualifier("embeddedConfigInfoPersistServiceImpl")
    private ConfigInfoPersistService configInfoPersistService;
    
    @PostConstruct
    public void start() {
        if (!configMapSyncConfig.isEnabled()) {
            Loggers.MAIN.info("The configMap-sync is disabled.");
            return;
        }
        Loggers.MAIN.info("Starting configMap-sync ...");
        startWatchConfigMap();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                Loggers.MAIN.info("Stopping configMap-sync ...");
                KubernetesConfigMapSyncServer.this.stop();
                Loggers.MAIN.info("ConfigMap-sync stopped...");
            }
        });
    }
    
    
    public void startWatchConfigMap() {
        CoreV1Api coreV1Api = new CoreV1Api();
        ApiClient apiClient = coreV1Api.getApiClient();
        OkHttpClient httpClient = apiClient.getHttpClient().newBuilder().build();
        apiClient.setHttpClient(httpClient);
        factory = new SharedInformerFactory(apiClient);
        SharedIndexInformer<V1ConfigMap> configInformer = factory.sharedIndexInformerFor(
                (CallGeneratorParams params) -> {
                    return coreV1Api.listConfigMapForAllNamespacesCall(true, null, null, null, null, null,
                            params.resourceVersion, null, params.timeoutSeconds, params.watch, null);
                }, V1ConfigMap.class, V1ConfigMapList.class);
        configInformer.addEventHandler(new ResourceEventHandler<V1ConfigMap>() {
            @Override
            public void onAdd(V1ConfigMap obj) {
                if (obj == null || obj.getMetadata() == null || obj.getData() == null) {
                    return;
                }
                Loggers.MAIN.info("Adding configMap...");
                ConfigInfo configInfo = configMapToNacosConfigInfo(obj);
                String srcIp = apiClient.getBasePath();
                
                try {
                    publishConfigMap(obj, srcIp);
                } catch (NacosException e) {
                    throw new RuntimeException(e);
                }
            }
            
            @Override
            public void onUpdate(V1ConfigMap oldObj, V1ConfigMap newObj) {
                Loggers.MAIN.info(
                        "Update configMap " + oldObj.getMetadata().getName() + " to " + newObj.getMetadata().getName());
                compareConfigMaps(oldObj, newObj);
                ConfigInfo configInfo = configMapToNacosConfigInfo(newObj);
                String srcIp = apiClient.getBasePath();
                try {
                    publishConfigMap(newObj, srcIp);
                } catch (NacosException e) {
                    throw new RuntimeException(e);
                }
            }
            
            @Override
            public void onDelete(V1ConfigMap obj, boolean deletedFinalStateUnknown) {
                if (obj == null || obj.getMetadata() == null || obj.getData() == null) {
                    return;
                }
                Loggers.MAIN.info("Delete configMap " + obj.getMetadata().getName());
                String srcIp = apiClient.getBasePath();
                deleteConfigMap(obj, srcIp);
            }
            
        });
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
    
    public ConfigInfo configMapToNacosConfigInfo(V1ConfigMap configMap) {
        Loggers.MAIN.info("Converting configMap to nacos ConfigInfo...");
        String dataId = configMap.getMetadata().getName();
        String group = ConfigMapSyncConfig.K8S_GROUP;
        String tenant = configMap.getMetadata().getNamespace();
        String appName = null;
        // 将 ConfigMap 的数据转换为 Nacos 的配置内容
        Map<String, String> dataMap = configMap.getData();
        String content = getContent(dataMap);
        return new ConfigInfo(dataId, group, tenant, appName, content);
    }
    
    public void publishConfigMap(V1ConfigMap configMap, String srcIp) throws NacosException {
        Loggers.MAIN.info("Converting configMap to nacos ConfigForm...");
        ConfigForm configForm = new ConfigForm();
        
        String namespaceId = configMap.getMetadata().getNamespace();
        String dataId = configMap.getMetadata().getName();
        String content = getContent(configMap.getData());
        String group = ConfigMapSyncConfig.K8S_GROUP;
        Pair<String, String> pair = EncryptionHandler.encryptHandler(dataId, content);
        content = pair.getSecond();
        String encryptedDataKey = pair.getFirst();
        
        ParamUtils.checkTenant(namespaceId);
        ParamUtils.checkParam(dataId, group, "datumId", content);
        
        configForm.setDataId(dataId);
        configForm.setGroup(group);
        configForm.setNamespaceId(namespaceId);
        configForm.setContent(content);
        configForm.setSrcUser(ConfigMapSyncConfig.SRC_USER);
        
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        configRequestInfo.setSrcIp(srcIp);
        
        configOperationService.publishConfig(configForm, configRequestInfo, encryptedDataKey);
    }
    
    public void deleteConfigMap(V1ConfigMap configMap, String clientIp) {
        String dataId = configMap.getMetadata().getName();
        String group = ConfigMapSyncConfig.K8S_GROUP;
        String namespaceId = configMap.getMetadata().getNamespace();
        String srcUser = ConfigMapSyncConfig.SRC_USER;
        configOperationService.deleteConfig(dataId, group, namespaceId, null, clientIp, srcUser);
    }
    
    public String getContent(Map<String, String> dataMap) {
        StringBuilder contentBuilder = new StringBuilder();
        for (Map.Entry<String, String> entry : dataMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            // 拼接配置项的格式，例如 key=value\n
            contentBuilder.append(key).append("=").append(value).append("\n");
        }
        return contentBuilder.toString();
    }
    
    
    public boolean isRunning() {
        return isRunning;
    }
    
    public void stop() {
        if (factory != null) {
            factory.stopAllRegisteredInformers();
        }
    }
}
