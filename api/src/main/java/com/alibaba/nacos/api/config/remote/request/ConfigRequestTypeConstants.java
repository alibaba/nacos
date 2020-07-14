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
package com.alibaba.nacos.api.config.remote.request;

import com.alibaba.nacos.api.remote.request.RequestTypeConstants;

/**
 * @author liuzunfei
 * @version $Id: ConfigRequestTypeConstants.java, v 0.1 2020年07月13日 9:09 PM liuzunfei Exp $
 */
public class ConfigRequestTypeConstants extends RequestTypeConstants {


    public static final String CHANGE_LISTEN_CONFIG_OPERATION="CHANGE_LISTEN_CONFIG_OPERATION";

    public static final String QUERY_CONFIG="QUERY_CONFIG";

    public static final String PUBLISH_CONFIG="PUBLISH_CONFIG";


}
