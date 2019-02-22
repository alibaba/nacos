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
 * Properties that are preferred to which in {@link PropertyKeyConst}
 *
 * @author pbting
 * @date 2019-02-22 3:38 PM
 */
public interface SystemPropertyKeyConst {

    String NAMING_SERVER_PORT = "nacos.naming.exposed.port";

    String NAMING_WEB_CONTEXT = "nacos.naming.web.context";

    String NACOS_NAMING_REQUEST_MODULE = "nacos.naming.request.module";
}
