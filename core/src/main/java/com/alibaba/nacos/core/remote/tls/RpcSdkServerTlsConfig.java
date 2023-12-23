package com.alibaba.nacos.core.remote.tls;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.PropertiesUtil;

/**
 * The {@code RpcSdkServerTlsConfig} class represents the TLS configuration for the Nacos Rpc server in SDK
 * communication type.
 *
 * <p>It extends the {@link RpcServerTlsConfig} class and provides specific configuration properties under the prefix
 * {@code nacos.remote.sdk.server.rpc.tls}. The TLS configuration is loaded from the environment using Spring Binder. If
 * the configuration is empty, it falls back to default values.
 * </p>
 *
 * <p>The class follows the Singleton pattern, and the instance can be obtained using the {@link #getInstance()}
 * method.
 * </p>
 *
 * <p>The logger messages include information about the type of TLS configuration, such as "Nacos Rpc SDK server tls
 * config."
 * </p>
 *
 * <p>Example Usage:
 * <pre>{@code
 * RpcSdkServerTlsConfig config = RpcSdkServerTlsConfig.getInstance();
 * }</pre>
 * </p>
 *
 * @author stone-98
 * @date 2023/12/23
 * @see RpcServerTlsConfig
 */
public class RpcSdkServerTlsConfig extends RpcServerTlsConfig {
    
    /**
     * The property key prefix for SDK TLS configuration.
     */
    public static final String PREFIX = "nacos.remote.sdk.server.rpc.tls";
    
    /**
     * The singleton instance of the RpcSdkServerTlsConfig class.
     */
    protected static RpcSdkServerTlsConfig instance;
    
    /**
     * Retrieves the singleton instance of RpcSdkServerTlsConfig, loading the TLS configuration from the environment
     * using Spring Binder. If the configuration is empty, it falls back to default values.
     *
     * @return The singleton instance of RpcSdkServerTlsConfig.
     */
    public static synchronized RpcSdkServerTlsConfig getInstance() {
        if (null == instance) {
            instance = PropertiesUtil.handleSpringBinder(EnvUtil.getEnvironment(), PREFIX, RpcSdkServerTlsConfig.class);
            if (instance == null) {
                Loggers.REMOTE.debug("SDK communication type TLS configuration is empty, use default value");
                instance = new RpcSdkServerTlsConfig();
            }
        }
        Loggers.REMOTE.info("Nacos Rpc SDK server tls config: {}", JacksonUtils.toJson(instance));
        return instance;
    }
}

