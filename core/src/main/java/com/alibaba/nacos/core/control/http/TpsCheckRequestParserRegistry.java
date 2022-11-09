package com.alibaba.nacos.core.control.http;

import com.alibaba.nacos.plugin.control.Loggers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TpsCheckRequestParserRegistry {
    
    static final Map<String, HttpTpsCheckParser> PARSER_MAP = new ConcurrentHashMap<>();
    
    public static void register(String pointName, HttpTpsCheckParser httpTpsCheckParser) {
        HttpTpsCheckParser prevTpsCheckParser = PARSER_MAP.put(pointName, httpTpsCheckParser);
        if (prevTpsCheckParser != null) {
            Loggers.CONTROL.info("RemoteTpsCheckParser {} of point name {} replaced with {}", pointName,
                    prevTpsCheckParser.getClass().getSimpleName(), httpTpsCheckParser.getClass().getSimpleName());
        }
    }
    
    public static HttpTpsCheckParser getParser(String pointName) {
        return PARSER_MAP.get(pointName);
    }
}
