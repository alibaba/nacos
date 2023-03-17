package com.alibaba.nacos.core.remote;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.sys.utils.ApplicationUtils;

import java.util.Collection;

public class RpcServerSslContextRefresherHolder {
    
    private static RpcServerSslContextRefresher INSTANCE;
    
    volatile private static boolean init = false;
    
    public static RpcServerSslContextRefresher getInstance() {
        if (init) {
            return INSTANCE;
        }
        synchronized (RpcServerSslContextRefresherHolder.class) {
            if (init) {
                return INSTANCE;
            }
            RpcServerTlsConfig rpcServerTlsConfig = ApplicationUtils.getBean(RpcServerTlsConfig.class);
            String sslContextRefresher = rpcServerTlsConfig.getSslContextRefresher();
            if (StringUtils.isNotBlank(sslContextRefresher)) {
                Collection<RpcServerSslContextRefresher> load = NacosServiceLoader.load(
                        RpcServerSslContextRefresher.class);
                for (RpcServerSslContextRefresher contextRefresher : load) {
                    if (sslContextRefresher.equals(contextRefresher.getName())) {
                        INSTANCE = contextRefresher;
                        Loggers.REMOTE.info("RpcServerSslContextRefresher of Name {} Founded->{}", sslContextRefresher,
                                contextRefresher.getClass().getSimpleName());
                        break;
                    }
                }
                if (INSTANCE == null) {
                    Loggers.REMOTE.info("RpcServerSslContextRefresher of Name {} not found", sslContextRefresher);
                }
                
            } else {
                Loggers.REMOTE.info(
                        "No RpcServerSslContextRefresher specified,Ssl Context auto refresh not supported.");
            }
            
            Loggers.REMOTE.info("RpcServerSslContextRefresher init end");
            init = true;
        }
        
        return INSTANCE;
    }
    
    
}
