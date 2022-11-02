package com.alibaba.nacos.core.remote.control;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TpsCheckRequestParserRegistry {
    
    static final Map<String, RemoteTpsCheckParser> parses = new ConcurrentHashMap<>();
    
    public static void register(String pointName, RemoteTpsCheckParser remoteTpsCheckParser) {
        RemoteTpsCheckParser prevRemoteTpsCheckParser = parses.put(pointName, remoteTpsCheckParser);
        if (remoteTpsCheckParser != null) {
            //TODO
        }
    }
    
    public static RemoteTpsCheckParser getParser(String pointName) {
        return parses.get(pointName);
    }
}
