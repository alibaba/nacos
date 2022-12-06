/*
 *
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.core.control.http;

import com.alibaba.nacos.plugin.control.Loggers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * http tps check request parse registry.
 *
 * @author shiyiyue
 */
public class HttpTpsCheckRequestParserRegistry {
    
    static final Map<String, HttpTpsCheckRequestParser> PARSER_MAP = new ConcurrentHashMap<>();
    
    /**
     * register httpTpsCheckParser.
     *
     * @param httpTpsCheckParser httpTpsCheckParser.
     */
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
