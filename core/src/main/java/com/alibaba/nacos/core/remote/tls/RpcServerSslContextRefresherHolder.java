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
 * ssl context refresher spi holder.
 *
 * @author liuzunfei
 * @version $Id: RequestFilters.java, v 0.1 2023年03月17日 12:00 PM liuzunfei Exp $
 */
public class RpcServerSslContextRefresherHolder {
    
    private static RpcServerSslContextRefresher instance;
    
    private static volatile boolean init = false;
    
    public static RpcServerSslContextRefresher getInstance() {
        if (init) {
            return instance;
        }
        synchronized (RpcServerSslContextRefresherHolder.class) {
            if (init) {
                return instance;
            }
            RpcServerTlsConfig rpcServerTlsConfig = RpcServerTlsConfig.getInstance();
            String sslContextRefresher = rpcServerTlsConfig.getSslContextRefresher();
            if (StringUtils.isNotBlank(sslContextRefresher)) {
                Collection<RpcServerSslContextRefresher> load = NacosServiceLoader
                        .load(RpcServerSslContextRefresher.class);
                for (RpcServerSslContextRefresher contextRefresher : load) {
                    if (sslContextRefresher.equals(contextRefresher.getName())) {
                        instance = contextRefresher;
                        Loggers.REMOTE.info("RpcServerSslContextRefresher of Name {} Founded->{}", sslContextRefresher,
                                contextRefresher.getClass().getSimpleName());
                        break;
                    }
                }
                if (instance == null) {
                    Loggers.REMOTE.info("RpcServerSslContextRefresher of Name {} not found", sslContextRefresher);
                }
                
            } else {
                Loggers.REMOTE
                        .info("No RpcServerSslContextRefresher specified,Ssl Context auto refresh not supported.");
            }
            
            Loggers.REMOTE.info("RpcServerSslContextRefresher init end");
            init = true;
        }
        
        return instance;
    }
    
}
