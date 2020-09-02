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

package com.alibaba.nacos.naming.remote.handler;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.HeartBeatResponse;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.naming.cluster.remote.request.ForwardHeartBeatRequest;
import com.alibaba.nacos.naming.remote.RemotingConnectionHolder;
import org.springframework.stereotype.Component;

/**
 * Forward heart beat request handler.
 *
 * @author xiweng.yy
 */
@Component
public class ForwardHeartBeatRequestHandler extends RequestHandler<ForwardHeartBeatRequest, HeartBeatResponse> {
    
    private final RemotingConnectionHolder remotingConnectionHolder;
    
    public ForwardHeartBeatRequestHandler(RemotingConnectionHolder remotingConnectionHolder) {
        this.remotingConnectionHolder = remotingConnectionHolder;
    }
    
    @Override
    public HeartBeatResponse handle(ForwardHeartBeatRequest request, RequestMeta meta) throws NacosException {
        remotingConnectionHolder.renewRemotingConnection(((ForwardHeartBeatRequest) request).getConnectionId());
        return new HeartBeatResponse();
    }
    
}
