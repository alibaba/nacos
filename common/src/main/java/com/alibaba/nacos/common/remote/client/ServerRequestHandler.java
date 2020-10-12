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

import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;

/**
 * ServerPushResponseHandler.
 *
 * @author liuzunfei
 * @version $Id: ServerPushResponseHandler.java, v 0.1 2020年07月14日 11:41 AM liuzunfei Exp $
 */
public interface ServerRequestHandler {
    
    /**
     * Handle reuqest from server.
     *
     * @param request request
     * @param requestMeta requestMeta
     * @return response.
     */
    Response requestReply(Request request, RequestMeta requestMeta);
    
}
