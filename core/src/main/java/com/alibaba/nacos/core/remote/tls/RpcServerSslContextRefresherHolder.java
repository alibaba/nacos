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
import com.alibaba.nacos.sys.env.EnvUtil;

import java.util.Collection;
import java.util.Properties;

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

    static {
        init();
    }

    /**
     * Gets the instance of {@link RpcServerSslContextRefresher} for SDK communication.
     *
     * @return The instance of {@link RpcServerSslContextRefresher} for SDK communication.
     */
    public static RpcServerSslContextRefresher getSdkInstance() {
        return sdkInstance;
    }

    /**
     * Gets the instance of {@link RpcServerSslContextRefresher} for Cluster communication.
     *
     * @return The instance of {@link RpcServerSslContextRefresher} for Cluster communication.
     */
    public static RpcServerSslContextRefresher getClusterInstance() {
        return clusterInstance;
    }

    /**
     * Initializes the holder by loading SSL context refreshers and matching them with the configured types (SDK and
     * Cluster).
     */
    private static void init() {
        synchronized (RpcServerSslContextRefresherHolder.class) {
            Properties properties = EnvUtil.getProperties();
            RpcServerTlsConfig clusterServerTlsConfig = RpcServerTlsConfigFactory.getInstance().createClusterConfig(properties);
            RpcServerTlsConfig sdkServerTlsConfig = RpcServerTlsConfigFactory.getInstance().createSdkConfig(properties);
            Collection<RpcServerSslContextRefresher> refreshers = NacosServiceLoader.load(
                    RpcServerSslContextRefresher.class);
            sdkInstance = getSslContextRefresher(refreshers, sdkServerTlsConfig);
            clusterInstance = getSslContextRefresher(refreshers, clusterServerTlsConfig);
            Loggers.REMOTE.info("RpcServerSslContextRefresher initialization completed.");
        }
    }

    /**
     * Initializes the SSL context refresher instance based on the specified configuration.
     *
     * @param refreshers        Collection of SSL context refreshers to choose from.
     * @param serverTlsConfig   Configuration instance for the SSL context refresher.
     * @return The instance of {@link RpcServerSslContextRefresher}.
     */
    private static RpcServerSslContextRefresher getSslContextRefresher(
            Collection<RpcServerSslContextRefresher> refreshers, RpcServerTlsConfig serverTlsConfig) {
        String refresherName = serverTlsConfig.getSslContextRefresher();
        RpcServerSslContextRefresher instance = null;
        if (StringUtils.isNotBlank(refresherName)) {
            for (RpcServerSslContextRefresher contextRefresher : refreshers) {
                if (refresherName.equals(contextRefresher.getName())) {
                    instance = contextRefresher;
                    Loggers.REMOTE.info("RpcServerSslContextRefresher initialized using {}.",
                            contextRefresher.getClass().getSimpleName());
                    break;
                }
            }
            if (instance == null) {
                Loggers.REMOTE.warn("Failed to find RpcServerSslContextRefresher with name {}.", refresherName);
            }
        } else {
            Loggers.REMOTE.info("Ssl Context auto refresh is not supported.");
        }
        return instance;
    }
}
