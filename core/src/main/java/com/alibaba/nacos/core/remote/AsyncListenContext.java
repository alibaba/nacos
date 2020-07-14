/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2020 All Rights Reserved.
 */
package com.alibaba.nacos.core.remote;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

/**
 * @author liuzunfei
 * @version $Id: AsyncListenContext.java, v 0.1 2020年07月14日 10:13 AM liuzunfei Exp $
 */
@Service
public class AsyncListenContext {

    private Map<String, Map<String,Set<String>>> listenContexts=new HashMap<String,Map<String,Set<String>>>() ;




    /**
     *
     * @param requestType
     * @param lisnteKey
     * @param connectionId
     */
    public void addListen(String requestType,String listenKey,String connectionId){
        Map<String, Set<String>> listenClients = listenContexts.get(requestType);

        if (listenClients==null){
            listenContexts.putIfAbsent(requestType,new HashMap<String,Set<String>>());
            listenClients=listenContexts.get(requestType);
        }

        Set<String> connectionIds = listenClients.get(listenKey);
        if (connectionIds==null){
            listenClients.putIfAbsent(listenKey,new HashSet<String>());
            connectionIds=listenClients.get(listenKey);
        }

        boolean addSuccess = connectionIds.add(connectionId);
        if (addSuccess){
            //TODO add log ...success to add listen

        }

    }


    public void removeListen(String requestType,String lisnteKey,String connectionId){

        Map<String, Set<String>> stringSetMap = listenContexts.get(requestType);
        if (stringSetMap==null||stringSetMap.isEmpty()){
            return;
        }

        Set<String> connectionIds = stringSetMap.get(lisnteKey);
        if (connectionIds==null){
            return;
        }

        boolean remove = connectionIds.remove(connectionId);
        if (remove){
            //TODO add log ...success to remove listen
        }
    }

}
