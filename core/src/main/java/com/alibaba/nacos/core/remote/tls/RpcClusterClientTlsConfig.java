package com.alibaba.nacos.core.remote.tls;

import com.alibaba.nacos.common.remote.TlsConfig;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.PropertiesUtil;

/**
 * RPC Cluster Client TLS Configuration for Nacos.
 * <p>
 * This class extends the {@link TlsConfig} class and provides a convenient way to create a configuration instance
 * specifically for the RPC (Remote Procedure Call) cluster client in Nacos.
 *
 * @author stone-98
 * @date 2023/12/20
 */
public class RpcClusterClientTlsConfig extends TlsConfig {
    
    /**
     * The property key for configuring RPC cluster client TLS settings.
     */
    public static final String NACOS_CLUSTER_CLIENT_RPC = "nacos.remote.cluster.client.rpc";
    
    /**
     * Creates a new instance of {@link RpcClusterClientTlsConfig} by loading TLS configuration from properties.
     *
     * @return A new instance of {@link RpcClusterClientTlsConfig} with loaded TLS configuration.
     */
    public static RpcClusterClientTlsConfig createConfig() {
        return PropertiesUtil.handleSpringBinder(EnvUtil.getEnvironment(), NACOS_CLUSTER_CLIENT_RPC,
                RpcClusterClientTlsConfig.class);
    }
}
