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

package com.alibaba.nacos.core.remote;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.HeartBeatRequest;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.HeartBeatResponse;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.core.remote.event.RemotingHeartBeatEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * HeartBeatRequestHandler.
 *
 * @author liuzunfei
 * @version $Id: HeartBeatRequestHandler.java, v 0.1 2020年07月14日 1:58 PM liuzunfei Exp $
 */
@Component
public class HeartBeatRequestHandler extends RequestHandler<HeartBeatRequest, HeartBeatResponse> {
    
    @Autowired
    ConnectionManager connectionManager;
    
    @Override
    public HeartBeatResponse handle(HeartBeatRequest request, RequestMeta meta) throws NacosException {
        String connectionId = meta.getConnectionId();
        connectionManager.refreshActiveTime(connectionId);
        NotifyCenter
                .publishEvent(new RemotingHeartBeatEvent(connectionId, meta.getClientIp(), meta.getClientVersion()));
        return new HeartBeatResponse();
    }
    
}
