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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.nacos.core.utils.Loggers;
import org.springframework.stereotype.Service;

/**
 * RequestHandlerRegistry.
 *
 * @author liuzunfei
 * @version $Id: RequestHandlerRegistry.java, v 0.1 2020年07月13日 8:24 PM liuzunfei Exp $
 */

@Service
public class RequestHandlerRegistry {
    
    Map<String, RequestHandler> registryHandlers = new HashMap<String, RequestHandler>();
    
    /**
     * Get Reuquest Handler By request Type.
     *
     * @param requestType see definitions  of sub constants classes of RequestTypeConstants
     * @return
     */
    public RequestHandler getByRequestType(String requestType) {
        return registryHandlers.get(requestType);
    }
    
    /**
     * registry request handler.
     *
     * @param requestHandler requestHandler to registry
     */
    public void registryHandler(RequestHandler requestHandler) {
        
        Loggers.GRPC.info(" register request handler, type={},handler={} ", requestHandler.getRequestTypes(),
                requestHandler.getClass().getSimpleName());
        List<String> requestTypes = requestHandler.getRequestTypes();
        for (String requestType : requestTypes) {
            //TODO should throw exception when hander conflicted.
            registryHandlers.putIfAbsent(requestType, requestHandler);
        }
    }
    
}
