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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.Pair;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.core.namespace.model.Namespace;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import com.alibaba.nacos.plugin.encryption.handler.EncryptionHandler;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.util.CallGeneratorParams;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * ConfigMap synchronization server.
 *
 * @author wangyixing
 */
@Service
public class KubernetesConfigMapSyncServer {
    
    private Gson gson = new Gson();
    
    private boolean isRunning;
    
    private SharedInformerFactory factory;
    
    @Autowired
    private ConfigMapSyncConfig configMapSyncConfig;
    
    @Autowired
    private ConfigOperationService configOperationService;
    
    @Autowired
    private NamespaceOperationService namespaceOperationService;
    
    @Autowired
    @Qualifier("embeddedConfigInfoPersistServiceImpl")
    private ConfigInfoPersistService configInfoPersistService;
    
    /**
     * start.
     */
    @PostConstruct
    public void start() throws IOException {
        if (!configMapSyncConfig.isEnabled()) {
            Loggers.MAIN.info("[{}] is disabled.", "configMap-sync");
            return;
        }
        Loggers.MAIN.info("[{}] starting...", "configMap-sync");
        startWatchConfigMap();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Loggers.MAIN.info("[{}] stopping...", "configMap-sync");
            KubernetesConfigMapSyncServer.this.stop();
            Loggers.MAIN.info("[{}] stopped.", "configMap-sync");
        }));
    }
    
    /**
     * use the Java API from an application outside a kubernetes cluster.
     * you should load a kubeConfig to generate apiClient instead of getting it from coreV1api.
     */
    public ApiClient getOutsideApiClient() throws IOException {
        String kubeConfigPath = configMapSyncConfig.getKubeConfig();
        
        // loading the out-of-cluster config, a kubeconfig from file-system
        ApiClient apiClient = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(new FileReader(kubeConfigPath))).build();
        
        // set the global default api-client to the in-cluster one from above
        Configuration.setDefaultApiClient(apiClient);
        return apiClient;
    }
    
    /**
     *  Start watch ConfigMap.
     */
    public void startWatchConfigMap() throws IOException {
        ApiClient apiClient;
        CoreV1Api coreV1Api;
        if (configMapSyncConfig.isOutsideCluster()) {
            Loggers.MAIN.info("[{}] use outside cluster Apiclient.", "configMap-sync");
            apiClient = getOutsideApiClient();
            coreV1Api = new CoreV1Api(apiClient);
        } else {
            Loggers.MAIN.info("[{}] use local cluster Apiclient.", "configMap-sync");
            coreV1Api = new CoreV1Api();
            apiClient = coreV1Api.getApiClient();
        }
        OkHttpClient httpClient = apiClient.getHttpClient().newBuilder().readTimeout(Duration.ZERO).build();
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
                Loggers.MAIN.info("[{}] adding configMap...", "configMap-sync");
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
                        "[{}] update configMap " + oldObj.getMetadata().getName() + " to " + newObj.getMetadata()
                                .getName(), "configMap-sync");
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
                Loggers.MAIN.info("[{}] delete configMap " + obj.getMetadata().getName(), "configMap-sync");
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
            Loggers.MAIN.error("[{}] Element is not json.", "configMap-sync");
        }
    }
    
    /**
     * compare old and new ConfigMap.
     * @param previousObj old ConfigMap
     * @param currentObj new ConfigMap
     */
    private void compareJsonObjects(JsonObject previousObj, JsonObject currentObj) {
        for (String key : previousObj.keySet()) {
            if (!currentObj.has(key)) {
                Loggers.MAIN.info("[{}] Field " + key + " removed.", "configMap-sync");
            } else if (!previousObj.get(key).equals(currentObj.get(key))) {
                Loggers.MAIN.info("[{}] Field " + key + " changed.", "configMap-sync");
                Loggers.MAIN.info("[{}] Previous value: " + previousObj.get(key), "configMap-sync");
                Loggers.MAIN.info("[{}] Current value: " + currentObj.get(key), "configMap-sync");
            }
        }
        
        for (String key : currentObj.keySet()) {
            if (!previousObj.has(key)) {
                Loggers.MAIN.info("[{}] Field " + key + " added.", "configMap-sync");
                Loggers.MAIN.info("[{}] Current value: " + currentObj.get(key), "configMap-sync");
            }
        }
    }
    
    /**
     * Convert ConfigMap to nacos ConfigInfo.
     * @param configMap k8sConfigMap
     * @return nacos configInfo
     */
    public ConfigInfo configMapToNacosConfigInfo(V1ConfigMap configMap) {
        Loggers.MAIN.info("[{}] Converting configMap to nacos ConfigInfo...", "configMap-sync");
        String dataId = configMap.getMetadata().getName();
        String group = ConfigMapSyncConfig.K8S_GROUP;
        String tenant = configMap.getMetadata().getNamespace();
        String appName = null;
        // 将 ConfigMap 的数据转换为 Nacos 的配置内容
        Map<String, String> dataMap = configMap.getData();
        String content = getContent(dataMap);
        return new ConfigInfo(dataId, group, tenant, appName, content);
    }
    
    /**
     * publish ConfigMap.
     * @param configMap k8sConfigMap
     * @param srcIp source Ip
     * @throws NacosException nacos exception
     */
    public void publishConfigMap(V1ConfigMap configMap, String srcIp) throws NacosException {
        Loggers.MAIN.info("[{}] Converting configMap to nacos ConfigForm...", "configMap-sync");
        
        final ConfigForm configForm = new ConfigForm();
        String configMapNamespace = configMap.getMetadata().getNamespace();
        List<Namespace> namespaceList = namespaceOperationService.getNamespaceList();
        String randomNamespace = UUID.randomUUID().toString();
        Optional<Namespace> matchingNamespace = namespaceList.stream()
                .filter(namespace -> Objects.equals(namespace.getNamespaceShowName(), configMapNamespace)).findFirst();
        if (!matchingNamespace.isPresent()) {
            namespaceOperationService.createNamespace(randomNamespace, configMapNamespace, "");
        }
        String namespaceId = matchingNamespace.map(Namespace::getNamespace).orElse(randomNamespace);
        String dataId = configMap.getMetadata().getName();
        String content = getContent(configMap.getData());
        String group = ConfigMapSyncConfig.K8S_GROUP;
        Pair<String, String> pair = EncryptionHandler.encryptHandler(dataId, content);
        content = pair.getSecond();
        final String encryptedDataKey = pair.getFirst();
        
        ParamUtils.checkTenant(namespaceId);
        ParamUtils.checkParam(dataId, group, "datumId", content);
        
        configForm.setDataId(dataId);
        configForm.setGroup(group);
        configForm.setNamespaceId(namespaceId);
        configForm.setContent(content);
        configForm.setSrcUser(ConfigMapSyncConfig.SRC_USER);
        
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        configRequestInfo.setSrcIp(truncateUrl(srcIp));
        
        configOperationService.publishConfig(configForm, configRequestInfo, encryptedDataKey);
    }
    
    /**
     * delete k8sConfigMap.
     * @param configMap k8sConfigMap
     * @param clientIp client Ip
     */
    public void deleteConfigMap(V1ConfigMap configMap, String clientIp) {
        String dataId = configMap.getMetadata().getName();
        String group = ConfigMapSyncConfig.K8S_GROUP;
        String configMapNamespace = configMap.getMetadata().getNamespace();
        List<Namespace> namespaceList = namespaceOperationService.getNamespaceList();
        Optional<Namespace> first = namespaceList.stream()
                .filter(namespace -> Objects.equals(namespace.getNamespaceShowName(), configMapNamespace)).findFirst();
        if (!first.isPresent()) {
            throw new RuntimeException("Not found namespace " + configMapNamespace + " in nacos.");
        }
        String namespaceId = first.get().getNamespace();
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
    
    /**
     * stop.
     */
    public void stop() {
        if (factory != null) {
            factory.stopAllRegisteredInformers();
        }
    }
    
    /**
     * truncateUrl in 20 characters.
     */
    public String truncateUrl(String url) {
        URI uri = null;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return url;
        }
        String protocol = uri.getScheme();
        String ip = uri.getHost();
        return protocol + "://" + ip;
    }
}
