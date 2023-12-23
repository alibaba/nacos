package com.alibaba.nacos.core.remote.tls;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.PropertiesUtil;

/**
 * The {@code RpcClusterServerTlsConfig} class represents the TLS configuration for the Nacos Rpc server in a cluster
 * environment.
 *
 * <p>It extends the {@link RpcServerTlsConfig} class and provides specific configuration properties under the prefix
 * {@code nacos.remote.cluster.server.rpc.tls}. The TLS configuration is loaded from the environment using Spring
 * Binder. If the configuration is empty, it falls back to default values.
 * </p>
 *
 * <p>The class follows the Singleton pattern, and the instance can be obtained using the {@link #getInstance()}
 * method.
 * </p>
 *
 * <p>The logger messages include information about the type of TLS configuration, such as "Nacos Rpc server tls
 * config."
 * </p>
 *
 * <p>Example Usage:
 * <pre>{@code
 * RpcClusterServerTlsConfig config = RpcClusterServerTlsConfig.getInstance();
 * }</pre>
 * </p>
 *
 * @author stone-98
 * @date 2023/12/23
 * @see RpcServerTlsConfig
 */
public class RpcClusterServerTlsConfig extends RpcServerTlsConfig {
    
    /**
     * The property key prefix for TLS configuration.
     */
    public static final String PREFIX = "nacos.remote.cluster.server.rpc.tls";
    
    /**
     * The singleton instance of the RpcClusterServerTlsConfig class.
     */
    protected static RpcClusterServerTlsConfig instance;
    
    /**
     * Retrieves the singleton instance of RpcClusterServerTlsConfig, loading the TLS configuration from the environment
     * using Spring Binder. If the configuration is empty, it falls back to default values.
     *
     * @return The singleton instance of RpcClusterServerTlsConfig.
     */
    public static synchronized RpcClusterServerTlsConfig getInstance() {
        if (null == instance) {
            instance = PropertiesUtil.handleSpringBinder(EnvUtil.getEnvironment(), PREFIX,
                    RpcClusterServerTlsConfig.class);
            if (instance == null) {
                Loggers.REMOTE.debug("Cluster communication type TLS configuration is empty, use default value");
                instance = new RpcClusterServerTlsConfig();
            }
        }
        Loggers.REMOTE.info("Nacos Rpc cluster server tls config: {}", JacksonUtils.toJson(instance));
        return instance;
    }
}

