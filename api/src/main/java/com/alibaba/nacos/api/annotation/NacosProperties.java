/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.annotation;

import com.alibaba.nacos.api.PropertyKeyConst;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation for Nacos Properties.
 *
 * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
 * @see PropertyKeyConst
 * @since 0.2.1
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NacosProperties {
    
    /**
     * The prefix of property name of Nacos.
     */
    String PREFIX = "nacos.";
    
    /**
     * The property name of "endpoint".
     */
    String ENDPOINT = "endpoint";
    
    /**
     * The property name of "namespace".
     */
    String NAMESPACE = "namespace";
    
    /**
     * The property name of "access-key".
     */
    String ACCESS_KEY = "access-key";
    
    /**
     * The property name of "secret-key".
     */
    String SECRET_KEY = "secret-key";
    
    /**
     * The property name of "server-addr".
     */
    String SERVER_ADDR = "server-addr";
    
    /**
     * The property name of "context-path".
     */
    String CONTEXT_PATH = "context-path";
    
    /**
     * The property name of "cluster-name".
     */
    String CLUSTER_NAME = "cluster-name";
    
    /**
     * The property name of "encode".
     */
    String ENCODE = "encode";
    
    /**
     * The property name of "long-poll.timeout".
     */
    String CONFIG_LONG_POLL_TIMEOUT = "configLongPollTimeout";
    
    /**
     * The property name of "config.retry.time".
     */
    String CONFIG_RETRY_TIME = "configRetryTime";
    
    /**
     * The property name of "maxRetry".
     */
    String MAX_RETRY = "maxRetry";
    
    /**
     * The property name of "enableRemoteSyncConfig".
     */
    String ENABLE_REMOTE_SYNC_CONFIG = "enableRemoteSyncConfig";
    
    /**
     * The property name of "username".
     */
    String USERNAME = "username";
    
    /**
     * The property name of "password".
     */
    String PASSWORD = "password";
    
    
    /**
     * The placeholder of endpoint, the value is <code>"${nacos.endpoint:}"</code>.
     */
    String ENDPOINT_PLACEHOLDER = "${" + PREFIX + ENDPOINT + ":}";
    
    /**
     * The placeholder of endpoint, the value is <code>"${nacos.namespace:}"</code>.
     */
    String NAMESPACE_PLACEHOLDER = "${" + PREFIX + NAMESPACE + ":}";
    
    /**
     * The placeholder of endpoint, the value is <code>"${nacos.access-key:}"</code>.
     */
    String ACCESS_KEY_PLACEHOLDER = "${" + PREFIX + ACCESS_KEY + ":}";
    
    /**
     * The placeholder of endpoint, the value is <code>"${nacos.secret-key:}"</code>.
     */
    String SECRET_KEY_PLACEHOLDER = "${" + PREFIX + SECRET_KEY + ":}";
    
    /**
     * The placeholder of endpoint, the value is <code>${nacos.server-addr:}"</code>.
     */
    String SERVER_ADDR_PLACEHOLDER = "${" + PREFIX + SERVER_ADDR + ":}";
    
    /**
     * The placeholder of endpoint, the value is ${nacos.context-path:}".
     */
    String CONTEXT_PATH_PLACEHOLDER = "${" + PREFIX + CONTEXT_PATH + ":}";
    
    /**
     * The placeholder of endpoint, the value is <code>"${nacos.cluster-name:}"</code>.
     */
    String CLUSTER_NAME_PLACEHOLDER = "${" + PREFIX + CLUSTER_NAME + ":}";
    
    /**
     * The placeholder of {@link NacosProperties#ENCODE encode}, the value is <code>"${nacos.encode:UTF-8}"</code>.
     */
    String ENCODE_PLACEHOLDER = "${" + PREFIX + ENCODE + ":UTF-8}";
    
    /**
     * The placeholder of {@link NacosProperties#CONFIG_LONG_POLL_TIMEOUT configLongPollTimeout}, the value is
     * <code>"${nacos.configLongPollTimeout:}"</code>.
     */
    String CONFIG_LONG_POLL_TIMEOUT_PLACEHOLDER = "${" + PREFIX + CONFIG_LONG_POLL_TIMEOUT + ":}";
    
    /**
     * The placeholder of {@link NacosProperties#CONFIG_RETRY_TIME configRetryTime}, the value is
     * <code>"${nacos.configRetryTime:}"</code>.
     */
    String CONFIG_RETRY_TIME_PLACEHOLDER = "${" + PREFIX + CONFIG_RETRY_TIME + ":}";
    
    /**
     * The placeholder of {@link NacosProperties#MAX_RETRY maxRetry}, the value is <code>"${nacos.maxRetry:}"</code>.
     */
    String MAX_RETRY_PLACEHOLDER = "${" + PREFIX + MAX_RETRY + ":}";
    
    /**
     * The placeholder of {@link NacosProperties#ENABLE_REMOTE_SYNC_CONFIG enableRemoteSyncConfig}, the value is
     * <code>"${nacos.enableRemoteSyncConfig:}"</code>.
     */
    String ENABLE_REMOTE_SYNC_CONFIG_PLACEHOLDER = "${" + PREFIX + ENABLE_REMOTE_SYNC_CONFIG + ":}";
    
    /**
     * The placeholder of endpoint, the value is <code>"${nacos.username:}"</code>.
     */
    String USERNAME_PLACEHOLDER = "${" + PREFIX + USERNAME + ":}";
    
    /**
     * The placeholder of endpoint, the value is <code>"${nacos.password:}"</code>.
     */
    String PASSWORD_PLACEHOLDER = "${" + PREFIX + PASSWORD + ":}";
    
    /**
     * The property of "endpoint".
     *
     * @return empty as default value
     * @see #ENDPOINT_PLACEHOLDER
     */
    String endpoint() default ENDPOINT_PLACEHOLDER;
    
    /**
     * The property of "namespace".
     *
     * @return empty as default value
     * @see #NAMESPACE_PLACEHOLDER
     */
    String namespace() default NAMESPACE_PLACEHOLDER;
    
    /**
     * The property of "access-key".
     *
     * @return empty as default value
     * @see #ACCESS_KEY_PLACEHOLDER
     */
    String accessKey() default ACCESS_KEY_PLACEHOLDER;
    
    /**
     * The property of "secret-key".
     *
     * @return empty as default value
     * @see #SECRET_KEY_PLACEHOLDER
     */
    String secretKey() default SECRET_KEY_PLACEHOLDER;
    
    /**
     * The property of "server-addr".
     *
     * @return empty as default value
     * @see #SERVER_ADDR_PLACEHOLDER
     */
    String serverAddr() default SERVER_ADDR_PLACEHOLDER;
    
    /**
     * The property of "context-path".
     *
     * @return empty as default value
     * @see #CONTEXT_PATH_PLACEHOLDER
     */
    String contextPath() default CONTEXT_PATH_PLACEHOLDER;
    
    /**
     * The property of "cluster-name".
     *
     * @return empty as default value
     * @see #CLUSTER_NAME_PLACEHOLDER
     */
    String clusterName() default CLUSTER_NAME_PLACEHOLDER;
    
    /**
     * The property of "encode".
     *
     * @return "UTF-8" as default value
     * @see #ENCODE_PLACEHOLDER
     */
    String encode() default ENCODE_PLACEHOLDER;
    
    /**
     * The property of "configLongPollTimeout".
     *
     * @return empty as default value
     * @see #CONFIG_LONG_POLL_TIMEOUT_PLACEHOLDER
     */
    String configLongPollTimeout() default CONFIG_LONG_POLL_TIMEOUT_PLACEHOLDER;
    
    /**
     * The property of "configRetryTime".
     *
     * @return empty as default value
     * @see #CONFIG_RETRY_TIME_PLACEHOLDER
     */
    String configRetryTime() default CONFIG_RETRY_TIME_PLACEHOLDER;
    
    /**
     * The property of "maxRetry".
     *
     * @return empty as default value
     * @see #MAX_RETRY
     */
    String maxRetry() default MAX_RETRY_PLACEHOLDER;
    
    /**
     * The property of "enableRemoteSyncConfig".
     *
     * @return empty as default value
     * @see #ENABLE_REMOTE_SYNC_CONFIG
     */
    String enableRemoteSyncConfig() default ENABLE_REMOTE_SYNC_CONFIG_PLACEHOLDER;
    
    /**
     * The property of "username".
     *
     * @return empty as default value
     * @see #USERNAME_PLACEHOLDER
     */
    String username() default USERNAME_PLACEHOLDER;
    
    /**
     * The property of "password".
     *
     * @return empty as default value
     * @see #PASSWORD_PLACEHOLDER
     */
    String password() default PASSWORD_PLACEHOLDER;
    
}
