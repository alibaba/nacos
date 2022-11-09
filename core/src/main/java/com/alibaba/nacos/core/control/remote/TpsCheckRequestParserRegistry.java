package com.alibaba.nacos.core.control.remote;

import com.alibaba.nacos.plugin.control.Loggers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TpsCheckRequestParserRegistry {
    
    static final Map<String, RemoteTpsCheckRequestParser> PARSER_MAP = new ConcurrentHashMap<>();
    
    public static void register(RemoteTpsCheckRequestParser remoteTpsCheckParser) {
        RemoteTpsCheckRequestParser prevRemoteTpsCheckParser = PARSER_MAP
                .put(remoteTpsCheckParser.getName(), remoteTpsCheckParser);
        if (prevRemoteTpsCheckParser != null) {
            Loggers.CONTROL.info("RemoteTpsCheckParser  name  {},point name {} will be replaced with {}",
                    remoteTpsCheckParser.getName(), remoteTpsCheckParser.getPointName(),
                    remoteTpsCheckParser.getClass().getSimpleName());
        } else {
            Loggers.CONTROL.info("RemoteTpsCheckParser register parser {} of name {},point name {}",
                    remoteTpsCheckParser.getClass().getSimpleName(), remoteTpsCheckParser.getName(),
                    remoteTpsCheckParser.getPointName());
        }
    }
    
    public static RemoteTpsCheckRequestParser getParser(String name) {
        return PARSER_MAP.get(name);
    }
}
