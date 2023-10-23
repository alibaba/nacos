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

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;

import java.io.FileReader;
import java.io.IOException;

/**
 * The ApiClient factory.
 *
 * @author wangyixing
 */
public class ApiClientFactory {
    
    /**
     * use the Java API from an application outside a kubernetes cluster.
     *
     * @param config ConfigMapSyncConfig
     * @return apiclient
     * @throws IOException exception
     */
    public static ApiClient createOutsideApiClient(ConfigMapSyncConfig config) throws IOException {
        String kubeConfigPath = config.getKubeConfig();
        
        // loading the out-of-cluster config, a kubeconfig from file-system
        ApiClient apiClient = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(new FileReader(kubeConfigPath)))
                .build();
        
        // set the global default api-client to the in-cluster one from above
        Configuration.setDefaultApiClient(apiClient);
        return apiClient;
    }
    
    /**
     * use the Java API from an application inside a kubernetes cluster.
     *
     * @return apiclient
     */
    public static ApiClient createInsideApiClient() {
        return Configuration.getDefaultApiClient();
    }
}
