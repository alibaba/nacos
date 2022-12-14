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

package com.alibaba.nacos.core.control.remote;

import com.alibaba.nacos.plugin.control.Loggers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * remote tps check request parser registry.
 *
 * @author shiyiyue
 */
public class RemoteTpsCheckRequestParserRegistry {
    
    static final Map<String, RemoteTpsCheckRequestParser> PARSER_MAP = new ConcurrentHashMap<>();
    
    /**
     * register remoteTpsCheckParser.
     *
     * @param remoteTpsCheckParser remoteTpsCheckParser.
     */
    public static void register(RemoteTpsCheckRequestParser remoteTpsCheckParser) {
        RemoteTpsCheckRequestParser prevRemoteTpsCheckParser = PARSER_MAP
                .put(remoteTpsCheckParser.getName(), remoteTpsCheckParser);
        if (prevRemoteTpsCheckParser != null) {
            Loggers.CONTROL.info("RemoteTpsCheckRequestParser  name  {},point name {} will be replaced with {}",
                    remoteTpsCheckParser.getName(), remoteTpsCheckParser.getPointName(),
                    remoteTpsCheckParser.getClass().getSimpleName());
        } else {
            Loggers.CONTROL.info("RemoteTpsCheckRequestParser register parser {} of name {},point name {}",
                    remoteTpsCheckParser.getClass().getSimpleName(), remoteTpsCheckParser.getName(),
                    remoteTpsCheckParser.getPointName());
        }
    }
    
    public static RemoteTpsCheckRequestParser getParser(String name) {
        return PARSER_MAP.get(name);
    }
}
