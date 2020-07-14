/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2020 All Rights Reserved.
 */
package com.alibaba.nacos.core.remote;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * @author liuzunfei
 * @version $Id: RequestHandlerRegistry.java, v 0.1 2020年07月13日 8:24 PM liuzunfei Exp $
 */

@Service
public class RequestHandlerRegistry {

    Map<String,RequestHandler> registryHandlers=new HashMap<String,RequestHandler>();

    /**
     *  Get Reuquest Handler By request Type
     *
     * @param requestType  see definitions  of sub constants classes of RequestTypeConstants
     * @return
     */
    public RequestHandler getByRequestType(String requestType){
        return registryHandlers.get(requestType);

    }


    public void registryHandler(RequestHandler requestHandler){
        List<String> requestTypes = requestHandler.getRequestTypes();
        for(String requestType:requestTypes){
            //TODO should throw exception when hander conflicted.
            registryHandlers.putIfAbsent(requestType,requestHandler);

        }


    }

}
