/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.auth.parser.grpc;

import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.auth.parser.AbstractResourceParser;
import com.alibaba.nacos.plugin.auth.constant.Constants;

import java.util.Properties;

/**
 * Abstract Grpc Resource Parser.
 *
 * @author xiweng.yy
 */
public abstract class AbstractGrpcResourceParser extends AbstractResourceParser<Request> {
    
    @Override
    protected Properties getProperties(Request request) {
        Properties properties = new Properties();
        properties.setProperty(Constants.Resource.REQUEST_CLASS, request.getClass().getSimpleName());
        return properties;
    }
}
