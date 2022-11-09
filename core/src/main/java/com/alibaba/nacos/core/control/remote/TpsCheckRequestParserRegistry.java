package com.alibaba.nacos.core.control.remote;

import com.alibaba.nacos.plugin.control.Loggers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TpsCheckRequestParserRegistry {
    
    static final Map<String, RemoteTpsCheckParser> PARSER_MAP = new ConcurrentHashMap<>();
    
    public static void register(String pointName, RemoteTpsCheckParser remoteTpsCheckParser) {
        RemoteTpsCheckParser prevRemoteTpsCheckParser = PARSER_MAP.put(pointName, remoteTpsCheckParser);
        if (prevRemoteTpsCheckParser != null) {
            Loggers.CONTROL.info("RemoteTpsCheckParser {} of point name {} replaced with {}", pointName,
                    prevRemoteTpsCheckParser.getClass().getSimpleName(),
                    remoteTpsCheckParser.getClass().getSimpleName());
        }
    }
    
    public static RemoteTpsCheckParser getParser(String pointName) {
        return PARSER_MAP.get(pointName);
    }
}
