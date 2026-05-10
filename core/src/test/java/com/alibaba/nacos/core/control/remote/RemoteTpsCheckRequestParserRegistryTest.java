/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.control.remote;

import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link RemoteTpsCheckRequestParserRegistry} unit test.
 *
 * @author shiyiyue
 */
class RemoteTpsCheckRequestParserRegistryTest {
    
    private static final String PARSER_NAME = "TestParser";
    
    @BeforeEach
    @AfterEach
    void clearRegistry() {
        RemoteTpsCheckRequestParserRegistry.PARSER_MAP.clear();
    }
    
    @Test
    void testRegisterAndGetParser() {
        RemoteTpsCheckRequestParser parser = new RemoteTpsCheckRequestParser() {
            
            @Override
            public TpsCheckRequest parse(Request request, RequestMeta meta) {
                return new TpsCheckRequest();
            }
            
            @Override
            public String getPointName() {
                return "testPoint";
            }
            
            @Override
            public String getName() {
                return PARSER_NAME;
            }
        };
        RemoteTpsCheckRequestParserRegistry.register(parser);
        RemoteTpsCheckRequestParser found =
            RemoteTpsCheckRequestParserRegistry.getParser(PARSER_NAME);
        assertNotNull(found);
        assertSame(parser, found);
    }
    
    @Test
    void testGetParserWhenNotRegistered() {
        assertNull(RemoteTpsCheckRequestParserRegistry.getParser("NonExistent"));
    }
    
    @Test
    void testRegisterReplacesPrevious() {
        RemoteTpsCheckRequestParser first = new RemoteTpsCheckRequestParser() {
            
            @Override
            public TpsCheckRequest parse(Request request, RequestMeta meta) {
                return new TpsCheckRequest();
            }
            
            @Override
            public String getPointName() {
                return "point1";
            }
            
            @Override
            public String getName() {
                return PARSER_NAME;
            }
        };
        RemoteTpsCheckRequestParser second = new RemoteTpsCheckRequestParser() {
            
            @Override
            public TpsCheckRequest parse(Request request, RequestMeta meta) {
                return new TpsCheckRequest();
            }
            
            @Override
            public String getPointName() {
                return "point2";
            }
            
            @Override
            public String getName() {
                return PARSER_NAME;
            }
        };
        RemoteTpsCheckRequestParserRegistry.register(first);
        RemoteTpsCheckRequestParserRegistry.register(second);
        assertSame(second, RemoteTpsCheckRequestParserRegistry.getParser(PARSER_NAME));
    }
}
