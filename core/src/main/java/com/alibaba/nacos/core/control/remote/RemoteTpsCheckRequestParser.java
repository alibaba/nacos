/*
 *
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
 *
 */

package com.alibaba.nacos.core.control.remote;

import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;

/**
 * remote tps check request parser.
 *
 * @author shiyiyue
 */
@SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
public abstract class RemoteTpsCheckRequestParser {
    
    public RemoteTpsCheckRequestParser() {
        RemoteTpsCheckRequestParserRegistry.register(this);
    }
    
    /**
     * parse tps check request.
     *
     * @param request request.
     * @param meta    meta.
     * @return
     */
    public abstract TpsCheckRequest parse(Request request, RequestMeta meta);
    
    /**
     * get point name.
     *
     * @return
     */
    public abstract String getPointName();
    
    /**
     * get name.
     *
     * @return
     */
    public abstract String getName();
}
