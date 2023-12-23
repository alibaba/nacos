/*
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
 */

package com.alibaba.nacos.core.remote.tls;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.utils.Loggers;

import java.util.Collection;

/**
 * Holder for managing instances of {@link RpcServerSslContextRefresher}. This class is responsible for initializing and
 * providing instances of the SSL context refresher based on the communication type (SDK or Cluster).
 *
 * @author liuzunfei
 * @version $Id: RpcServerSslContextRefresherHolder.java, v 0.1 2023年03月17日 12:00 PM liuzunfei Exp $
 */
public class RpcServerSslContextRefresherHolder {
    
    /**
     * The instance of {@link RpcServerSslContextRefresher} for SDK communication.
     */
    private static RpcServerSslContextRefresher sdkInstance;
    
    /**
     * The instance of {@link RpcServerSslContextRefresher} for Cluster communication.
     */
    private static RpcServerSslContextRefresher clusterInstance;
    
    /**
     * Flag indicating whether the holder has been initialized.
     */
    private static volatile boolean init = false;
    
    /**
     * Gets the instance of {@link RpcServerSslContextRefresher} for SDK communication.
     *
     * @return The instance of {@link RpcServerSslContextRefresher} for SDK communication.
     */
    public static RpcServerSslContextRefresher getSdkInstance() {
        init();
        return sdkInstance;
    }
    
    /**
     * Gets the instance of {@link RpcServerSslContextRefresher} for Cluster communication.
     *
     * @return The instance of {@link RpcServerSslContextRefresher} for Cluster communication.
     */
    public static RpcServerSslContextRefresher getClusterInstance() {
        init();
        return clusterInstance;
    }
    
    /**
     * Initializes the holder by loading SSL context refreshers and matching them with the configured types (SDK and
     * Cluster).
     */
    public static void init() {
        if (init) {
            return;
        }
        synchronized (RpcServerSslContextRefresherHolder.class) {
            if (init) {
                return;
            }
            RpcSdkServerTlsConfig rpcSdkServerTlsConfig = RpcSdkServerTlsConfig.getInstance();
            RpcClusterServerTlsConfig rpcClusterServerTlsConfig = RpcClusterServerTlsConfig.getInstance();
            String sdkSslContextRefresher = rpcSdkServerTlsConfig.getSslContextRefresher();
            String clusterSslContextRefresher = rpcClusterServerTlsConfig.getSslContextRefresher();
            if (StringUtils.isBlank(sdkSslContextRefresher)) {
                Loggers.REMOTE.info(
                        "No SDK communication type RpcServerSslContextRefresher specified, Ssl Context auto refresh not supported.");
            } else {
                Loggers.REMOTE.info("Configured SDK communication type RpcServerSslContextRefresher: {}",
                        sdkSslContextRefresher);
            }
            
            if (StringUtils.isBlank(clusterSslContextRefresher)) {
                Loggers.REMOTE.info(
                        "No cluster communication type RpcServerSslContextRefresher specified, Ssl Context auto refresh not supported.");
            } else {
                Loggers.REMOTE.info("Configured Cluster communication type RpcServerSslContextRefresher: {}",
                        clusterSslContextRefresher);
            }
            Collection<RpcServerSslContextRefresher> load = NacosServiceLoader.load(RpcServerSslContextRefresher.class);
            for (RpcServerSslContextRefresher contextRefresher : load) {
                if (sdkSslContextRefresher.equals(contextRefresher.getName())) {
                    sdkInstance = contextRefresher;
                    Loggers.REMOTE.info("RpcServerSslContextRefresher of Name {} for SDK Founded -> {}",
                            sdkSslContextRefresher, contextRefresher.getClass().getSimpleName());
                    break;
                }
            }
            
            for (RpcServerSslContextRefresher contextRefresher : load) {
                if (clusterSslContextRefresher.equals(contextRefresher.getName())) {
                    clusterInstance = contextRefresher;
                    Loggers.REMOTE.info("RpcServerSslContextRefresher of Name {} for Cluster Founded -> {}",
                            clusterSslContextRefresher, contextRefresher.getClass().getSimpleName());
                    break;
                }
            }
            
            if (sdkInstance == null) {
                Loggers.REMOTE.info("RpcServerSslContextRefresher of Name {} for SDK not found",
                        sdkSslContextRefresher);
            }
            
            if (clusterInstance == null) {
                Loggers.REMOTE.info("RpcServerSslContextRefresher of Name {} for Cluster not found",
                        clusterSslContextRefresher);
            }
            
            Loggers.REMOTE.info("RpcServerSslContextRefresher init end");
            init = true;
        }
    }
    
}
