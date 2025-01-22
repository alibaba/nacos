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

package com.alibaba.nacos.maintainer.client.remote;

import com.alibaba.nacos.maintainer.client.utils.ParamUtil;

/**
 * default http client factory.
 *
 * @author Nacos
 */
public class DefaultHttpClientFactory extends AbstractHttpClientFactory {
    
    private static final int CON_TIME_OUT_MILLIS = ParamUtil.getConnectTimeout();
    
    private static final int READ_TIME_OUT_MILLIS = ParamUtil.getReadTimeout();
    
    @Override
    protected HttpClientConfig buildHttpClientConfig() {
        return HttpClientConfig.builder().setConTimeOutMillis(CON_TIME_OUT_MILLIS)
                .setReadTimeOutMillis(READ_TIME_OUT_MILLIS).build();
    }
}
