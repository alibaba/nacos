package com.alibaba.nacos.core.remote.tls;

/**
 * RpcConstants.
 *
 * @author stone-98
 * @date 2024/4/8
 */
public class RpcServerConstants {
    
    public static final String NACOS_SERVER_RPC = "nacos.remote.server.rpc.tls";
    
    public static final String NACOS_CLUSTER_SERVER_RPC = "nacos.remote.cluster.server.rpc.tls";
    
    /**
     * Enumeration of common suffixes for RPC configuration properties. Each enum constant represents a specific
     * configuration attribute suffix. This allows for the construction of complete configuration property keys.
     */
    public class ServerSuffix {
        
        /**
         * Suffix for 'tls.enable' configuration property.
         */
        public static final String TLS_ENABLE = ".enableTls";
        
        /**
         * Suffix for 'tls.provider' configuration property.
         */
        public static final String TLS_PROVIDER = ".sslProvider";
        
        /**
         * Suffix for 'tls.mutualAuth' configuration property.
         */
        public static final String MUTUAL_AUTH = ".mutualAuthEnable";
        
        /**
         * Suffix for 'tls.protocols' configuration property.
         */
        public static final String TLS_PROTOCOLS = ".protocols";
        
        /**
         * Suffix for 'tls.ciphers' configuration property.
         */
        public static final String TLS_CIPHERS = ".ciphers";
        
        /**
         * Suffix for 'tls.certChainFile' configuration property.
         */
        public static final String TLS_CERT_CHAIN_PATH = ".certChainFile";
        
        /**
         * Suffix for 'tls.certPrivateKey' configuration property.
         */
        public static final String TLS_CERT_KEY = ".certPrivateKey";
        
        /**
         * Suffix for 'tls.certPrivateKeyPassword' configuration property.
         */
        public static final String TLS_TRUST_PWD = ".certPrivateKeyPassword";
        
        /**
         * Suffix for 'tls.trustCollectionChainPath' configuration property.
         */
        public static final String TLS_TRUST_COLLECTION_CHAIN_PATH = ".trustCollectionCertFile";
        
        /**
         * Suffix for 'tls.trustAll' configuration property.
         */
        public static final String TLS_TRUST_ALL = ".trustAll";
        
        /**
         * Suffix for '.sslContextRefresher' configuration property.
         */
        public static final String SSL_CONTEXT_REFRESHER = ".sslContextRefresher";
        
        /**
         * Suffix for '.compatibility' configuration property.
         */
        public static final String COMPATIBILITY = ".compatibility";
    }
}
