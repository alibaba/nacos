/*
 * Copyright (C) 2019 the original author or authors.
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
package com.alibaba.nacos.api;

/**
 * <p>
 * 支持从 -D 参数中读取指定变量的值
 * </P>
 * Properties that are preferred to which in {@link PropertyKeyConst}
 *
 * @author pbting
 * @date 2019-02-22 3:38 PM
 */
public interface SystemPropertyKeyConst {

    String NAMING_SERVER_PORT = "nacos.naming.exposed.port";

    String NAMING_WEB_CONTEXT = "nacos.naming.web.context";

    /**
     * 在云(阿里云或者其他云厂商 )环境下，是否启用云环境下的 namespace 解析。
     * <p>
     * 默认是打开的。
     * </p>
     */
    String IS_USE_CLOUD_NAMESPACE_PARSING = "is.use.cloud.namespace.parsing";

    /**
     * 云环境下，如果进程级需要一个全局统一的 namespace，可以通过 -D 参数指定。
     */
    String ANS_NAMESPACE = "ans.namespace";

    /**
     * 也支持通过 -D 参数来指定。
     */
    String IS_USE_ENDPOINT_PARSING_RULE = "is.use.endpoint.parsing.rule";
}
