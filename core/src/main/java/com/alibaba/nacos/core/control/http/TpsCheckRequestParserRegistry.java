package com.alibaba.nacos.core.control.http;

import com.alibaba.nacos.plugin.control.Loggers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TpsCheckRequestParserRegistry {
    
    static final Map<String, HttpTpsCheckRequestParser> PARSER_MAP = new ConcurrentHashMap<>();
    
    public static synchronized void register(HttpTpsCheckRequestParser httpTpsCheckParser) {
        HttpTpsCheckRequestParser prevTpsCheckParser = PARSER_MAP.put(httpTpsCheckParser.getName(), httpTpsCheckParser);
        if (prevTpsCheckParser != null) {
            Loggers.CONTROL.info("HttpTpsCheckRequestParser  name {}, point name {} will be replaced with {}",
                    httpTpsCheckParser.getName(), prevTpsCheckParser.getPointName(),
                    httpTpsCheckParser.getClass().getSimpleName());
        } else {
            Loggers.CONTROL.info("HttpTpsCheckRequestParser register parser {} of name {},point name {}",
                    httpTpsCheckParser.getClass().getSimpleName(), httpTpsCheckParser.getName(),
                    httpTpsCheckParser.getPointName());
        }
    }
    
    public static HttpTpsCheckRequestParser getParser(String pointName) {
        return PARSER_MAP.get(pointName);
    }
}
