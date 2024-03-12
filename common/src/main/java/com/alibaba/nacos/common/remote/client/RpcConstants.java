/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.remote.client;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * RpcConstants.
 *
 * @author githubcheng2978.
 */
public class RpcConstants {
    
    public static final String NACOS_CLIENT_RPC = "nacos.remote.client.rpc";
    
    public static final String NACOS_CLUSTER_CLIENT_RPC = "nacos.remote.cluster.client.rpc";

    public static final String NACOS_SERVER_RPC = "nacos.remote.server.rpc.tls";

    public static final String NACOS_CLUSTER_SERVER_RPC = "nacos.remote.cluster.server.rpc.tls";
    
    @RpcConfigLabel
    public static final String RPC_CLIENT_TLS_ENABLE = getConfigKey(RpcClientConfigSuffix.TLS_ENABLE);
    
    @RpcConfigLabel
    public static final String RPC_CLIENT_TLS_PROVIDER = getConfigKey(RpcClientConfigSuffix.TLS_PROVIDER);
    
    @RpcConfigLabel
    public static final String RPC_CLIENT_MUTUAL_AUTH = getConfigKey(RpcClientConfigSuffix.MUTUAL_AUTH);
    
    @RpcConfigLabel
    public static final String RPC_CLIENT_TLS_PROTOCOLS = getConfigKey(RpcClientConfigSuffix.TLS_PROTOCOLS);
    
    @RpcConfigLabel
    public static final String RPC_CLIENT_TLS_CIPHERS = getConfigKey(RpcClientConfigSuffix.TLS_CIPHERS);
    
    @RpcConfigLabel
    public static final String RPC_CLIENT_TLS_CERT_CHAIN_PATH = getConfigKey(RpcClientConfigSuffix.TLS_CERT_CHAIN_PATH);
    
    @RpcConfigLabel
    public static final String RPC_CLIENT_TLS_CERT_KEY = getConfigKey(RpcClientConfigSuffix.TLS_CERT_KEY);
    
    @RpcConfigLabel
    public static final String RPC_CLIENT_TLS_TRUST_PWD = getConfigKey(RpcClientConfigSuffix.TLS_TRUST_PWD);
    
    @RpcConfigLabel
    public static final String RPC_CLIENT_TLS_TRUST_COLLECTION_CHAIN_PATH = getConfigKey(
            RpcClientConfigSuffix.TLS_TRUST_COLLECTION_CHAIN_PATH);
    
    @RpcConfigLabel
    public static final String RPC_CLIENT_TLS_TRUST_ALL = getConfigKey(RpcClientConfigSuffix.TLS_TRUST_ALL);
    
    private static final Set<String> CONFIG_NAMES = new HashSet<>();
    
    @RpcClusterConfigLabel
    public static final String RPC_CLUSTER_CLIENT_TLS_ENABLE = getClusterConfigKey(RpcClientConfigSuffix.TLS_ENABLE);
    
    @RpcClusterConfigLabel
    public static final String RPC_CLUSTER_CLIENT_TLS_PROVIDER = getClusterConfigKey(RpcClientConfigSuffix.TLS_PROVIDER);
    
    @RpcClusterConfigLabel
    public static final String RPC_CLUSTER_CLIENT_MUTUAL_AUTH = getClusterConfigKey(RpcClientConfigSuffix.MUTUAL_AUTH);
    
    @RpcClusterConfigLabel
    public static final String RPC_CLUSTER_CLIENT_TLS_PROTOCOLS = getClusterConfigKey(RpcClientConfigSuffix.TLS_PROTOCOLS);
    
    @RpcClusterConfigLabel
    public static final String RPC_CLUSTER_CLIENT_TLS_CIPHERS = getClusterConfigKey(RpcClientConfigSuffix.TLS_CIPHERS);
    
    @RpcClusterConfigLabel
    public static final String RPC_CLUSTER_CLIENT_TLS_CERT_CHAIN_PATH = getClusterConfigKey(
            RpcClientConfigSuffix.TLS_CERT_CHAIN_PATH);
    
    @RpcClusterConfigLabel
    public static final String RPC_CLUSTER_CLIENT_TLS_CERT_KEY = getClusterConfigKey(RpcClientConfigSuffix.TLS_CERT_KEY);
    
    @RpcClusterConfigLabel
    public static final String RPC_CLUSTER_CLIENT_TLS_TRUST_PWD = getClusterConfigKey(RpcClientConfigSuffix.TLS_TRUST_PWD);
    
    @RpcClusterConfigLabel
    public static final String RPC_CLUSTER_CLIENT_TLS_TRUST_COLLECTION_CHAIN_PATH = getClusterConfigKey(
            RpcClientConfigSuffix.TLS_TRUST_COLLECTION_CHAIN_PATH);
    
    @RpcClusterConfigLabel
    public static final String RPC_CLUSTER_CLIENT_TLS_TRUST_ALL = getClusterConfigKey(RpcClientConfigSuffix.TLS_TRUST_ALL);
    
    private static final Set<String> CLUSTER_CONFIG_NAMES = new HashSet<>();
    
    static {
        Class clazz = RpcConstants.class;
        Field[] declaredFields = clazz.getDeclaredFields();
        for (Field declaredField : declaredFields) {
            declaredField.setAccessible(true);
            if (!declaredField.getType().equals(String.class)) {
                continue;
            }
            if (null != declaredField.getAnnotation(RpcConfigLabel.class)) {
                try {
                    CONFIG_NAMES.add((String) declaredField.get(null));
                } catch (IllegalAccessException ignored) {
                }
            } else if (null != declaredField.getAnnotation(RpcClusterConfigLabel.class)) {
                try {
                    CLUSTER_CONFIG_NAMES.add((String) declaredField.get(null));
                } catch (IllegalAccessException ignored) {
                }
            }
        }
    }
    
    public static String getConfigKey(RpcClientConfigSuffix configSuffix) {
        return NACOS_CLIENT_RPC + configSuffix.getSuffix();
    }
    
    public static String getClusterConfigKey(RpcClientConfigSuffix configSuffix) {
        return NACOS_CLUSTER_CLIENT_RPC + configSuffix.getSuffix();
    }
    
    /**
     * Enumeration of common suffixes for RPC configuration properties. Each enum constant represents a specific
     * configuration attribute suffix. This allows for the construction of complete configuration property keys.
     */
    public enum RpcClientConfigSuffix {
        
        /**
         * Suffix for 'tls.enable' configuration property.
         */
        TLS_ENABLE(".tls.enable"),
        
        /**
         * Suffix for 'tls.provider' configuration property.
         */
        TLS_PROVIDER(".tls.provider"),
        
        /**
         * Suffix for 'tls.mutualAuth' configuration property.
         */
        MUTUAL_AUTH(".tls.mutualAuth"),
        
        /**
         * Suffix for 'tls.protocols' configuration property.
         */
        TLS_PROTOCOLS(".tls.protocols"),
        
        /**
         * Suffix for 'tls.ciphers' configuration property.
         */
        TLS_CIPHERS(".tls.ciphers"),
        
        /**
         * Suffix for 'tls.certChainFile' configuration property.
         */
        TLS_CERT_CHAIN_PATH(".tls.certChainFile"),
        
        /**
         * Suffix for 'tls.certPrivateKey' configuration property.
         */
        TLS_CERT_KEY(".tls.certPrivateKey"),
        
        /**
         * Suffix for 'tls.certPrivateKeyPassword' configuration property.
         */
        TLS_TRUST_PWD(".tls.certPrivateKeyPassword"),
        
        /**
         * Suffix for 'tls.trustCollectionChainPath' configuration property.
         */
        TLS_TRUST_COLLECTION_CHAIN_PATH(".tls.trustCollectionChainPath"),
        
        /**
         * Suffix for 'tls.trustAll' configuration property.
         */
        TLS_TRUST_ALL(".tls.trustAll");
        
        private final String suffix;
        
        /**
         * Constructor for RpcClientConfigSuffix enumeration.
         *
         * @param suffix The configuration attribute suffix.
         */
        RpcClientConfigSuffix(String suffix) {
            this.suffix = suffix;
        }
        
        /**
         * Retrieve the configuration attribute suffix.
         *
         * @return The configuration attribute suffix.
         */
        public String getSuffix() {
            return suffix;
        }
    }

    /**
     * Enumeration of common suffixes for RPC configuration properties. Each enum constant represents a specific
     * configuration attribute suffix. This allows for the construction of complete configuration property keys.
     */
    public enum RpcServerConfigSuffix {

        /**
         * Suffix for 'tls.enable' configuration property.
         */
        TLS_ENABLE(".enableTls"),

        /**
         * Suffix for 'tls.provider' configuration property.
         */
        TLS_PROVIDER(".sslProvider"),

        /**
         * Suffix for 'tls.mutualAuth' configuration property.
         */
        MUTUAL_AUTH(".mutualAuthEnable"),

        /**
         * Suffix for 'tls.protocols' configuration property.
         */
        TLS_PROTOCOLS(".protocols"),

        /**
         * Suffix for 'tls.ciphers' configuration property.
         */
        TLS_CIPHERS(".ciphers"),

        /**
         * Suffix for 'tls.certChainFile' configuration property.
         */
        TLS_CERT_CHAIN_PATH(".certChainFile"),

        /**
         * Suffix for 'tls.certPrivateKey' configuration property.
         */
        TLS_CERT_KEY(".certPrivateKey"),

        /**
         * Suffix for 'tls.certPrivateKeyPassword' configuration property.
         */
        TLS_TRUST_PWD(".certPrivateKeyPassword"),

        /**
         * Suffix for 'tls.trustCollectionChainPath' configuration property.
         */
        TLS_TRUST_COLLECTION_CHAIN_PATH(".trustCollectionCertFile"),

        /**
         * Suffix for 'tls.trustAll' configuration property.
         */
        TLS_TRUST_ALL(".trustAll"),

        SSL_CONTEXT_REFRESHER(".sslContextRefresher"),

        COMPATIBILITY(".compatibility");

        private final String suffix;

        /**
         * Constructor for RpcServerConfigSuffix enumeration.
         *
         * @param suffix The configuration attribute suffix.
         */
        RpcServerConfigSuffix(String suffix) {
            this.suffix = suffix;
        }

        /**
         * Retrieve the configuration attribute suffix.
         *
         * @return The configuration attribute suffix.
         */
        public String getSuffix() {
            return suffix;
        }
    }
    
    
    @Documented
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    protected @interface RpcConfigLabel {
    
    }
    
    @Documented
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    protected @interface RpcClusterConfigLabel {
    
    }
    
    public static Set<String> getRpcParams() {
        return Collections.unmodifiableSet(CONFIG_NAMES);
    }
    
    public static Set<String> getClusterRpcParams() {
        return Collections.unmodifiableSet(CLUSTER_CONFIG_NAMES);
    }
}
