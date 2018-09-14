/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.nacos.api.config.annotation;

import com.alibaba.nacos.api.PropertyKeyConst;

import java.lang.annotation.*;

/**
 * An annotation for Nacos Properties
 *
 * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
 * @see PropertyKeyConst
 * @since 0.1.0
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NacosProperties {

    /**
     * The prefix of property name of Nacos
     */
    String PREFIX = "nacos.";

    /**
     * The property name of "endpoint"
     */
    String ENDPOINT = "endpoint";

    /**
     * The property name of "namespace"
     */
    String NAMESPACE = "namespace";

    /**
     * The property name of "access-key"
     */
    String ACCESS_KEY = "access-key";

    /**
     * The property name of "secret-key"
     */
    String SECRET_KEY = "secret-key";

    /**
     * The property name of "server-addr"
     */
    String SERVER_ADDR = "server-addr";

    /**
     * The property name of "context-path"
     */
    String CONTEXT_PATH = "context-path";

    /**
     * The property name of "cluster-name"
     */
    String CLUSTER_NAME = "cluster-name";

    /**
     * The property name of "encode"
     */
    String ENCODE = "encode";

    /**
     * The property of "endpoint"
     *
     * @return empty as default value
     */
    String endpoint() default "${" + PREFIX + ENDPOINT + ":}";

    /**
     * The property of "namespace"
     *
     * @return empty as default value
     */
    String namespace() default "${" + PREFIX + NAMESPACE + ":}";

    /**
     * The property of "access-key"
     *
     * @return empty as default value
     */
    String accessKey() default "${" + PREFIX + ACCESS_KEY + ":}";

    /**
     * The property of "secret-key"
     *
     * @return empty as default value
     */
    String secretKey() default "${" + PREFIX + SECRET_KEY + ":}";

    /**
     * The property of "server-addr"
     *
     * @return empty as default value
     */
    String serverAddr() default "${" + PREFIX + SERVER_ADDR + ":}";

    /**
     * The property of "context-path"
     *
     * @return empty as default value
     */
    String contextPath() default "${" + PREFIX + CONTEXT_PATH + ":}";

    /**
     * The property of "cluster-name"
     *
     * @return empty as default value
     */
    String clusterName() default "${" + PREFIX + CLUSTER_NAME + ":}";

    /**
     * The property of "encode"
     *
     * @return "UTF-8" as default value
     */
    String encode() default "${" + PREFIX + ENCODE + ":UTF-8}";

}
