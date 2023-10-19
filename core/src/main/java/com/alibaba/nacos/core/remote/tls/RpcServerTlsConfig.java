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

import com.alibaba.nacos.common.remote.TlsConfig;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.PropertiesUtil;

/**
 * Grpc config.
 *
 * @author githubcheng2978.
 */
public class RpcServerTlsConfig extends TlsConfig {
    
    public static final String PREFIX = "nacos.remote.server.rpc.tls";
    
    private static RpcServerTlsConfig instance;
    
    private String sslContextRefresher = "";
    
    private Boolean compatibility = true;
    
    public static synchronized RpcServerTlsConfig getInstance() {
        if (null == instance) {
            instance = PropertiesUtil.handleSpringBinder(EnvUtil.getEnvironment(), PREFIX, RpcServerTlsConfig.class);
            if (instance == null) {
                Loggers.REMOTE.debug("TLS configuration is empty, use default value");
                instance = new RpcServerTlsConfig();
            }
        }
        Loggers.REMOTE.info("Nacos Rpc server tls config:{}", JacksonUtils.toJson(instance));
        return instance;
    }
    
    public Boolean getCompatibility() {
        return compatibility;
    }
    
    public void setCompatibility(Boolean compatibility) {
        this.compatibility = compatibility;
    }
    
    public String getSslContextRefresher() {
        return sslContextRefresher;
    }
    
    public void setSslContextRefresher(String sslContextRefresher) {
        this.sslContextRefresher = sslContextRefresher;
    }
}
