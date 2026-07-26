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

package com.alibaba.nacos.core.control.http;

import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link HttpTpsCheckRequestParserRegistry} unit test.
 *
 * @author shiyiyue
 */
class HttpTpsCheckRequestParserRegistryTest {
    
    private static final String PARSER_NAME = "TestHttpParser";
    
    @BeforeEach
    @AfterEach
    void clearRegistry() {
        HttpTpsCheckRequestParserRegistry.PARSER_MAP.clear();
    }
    
    @Test
    void testRegisterAndGetParser() {
        HttpTpsCheckRequestParser parser = new HttpTpsCheckRequestParser() {
            
            @Override
            public TpsCheckRequest parse(HttpServletRequest httpServletRequest) {
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
        HttpTpsCheckRequestParserRegistry.register(parser);
        HttpTpsCheckRequestParser found = HttpTpsCheckRequestParserRegistry.getParser(PARSER_NAME);
        assertNotNull(found);
        assertSame(parser, found);
    }
    
    @Test
    void testGetParserWhenNotRegistered() {
        assertNull(HttpTpsCheckRequestParserRegistry.getParser("NonExistent"));
    }
    
    @Test
    void testRegisterReplacesPrevious() {
        HttpTpsCheckRequestParser first = new HttpTpsCheckRequestParser() {
            
            @Override
            public TpsCheckRequest parse(HttpServletRequest httpServletRequest) {
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
        HttpTpsCheckRequestParser second = new HttpTpsCheckRequestParser() {
            
            @Override
            public TpsCheckRequest parse(HttpServletRequest httpServletRequest) {
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
        HttpTpsCheckRequestParserRegistry.register(first);
        HttpTpsCheckRequestParserRegistry.register(second);
        assertSame(second, HttpTpsCheckRequestParserRegistry.getParser(PARSER_NAME));
    }
}
