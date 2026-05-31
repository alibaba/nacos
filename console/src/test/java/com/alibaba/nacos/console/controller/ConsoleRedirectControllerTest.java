/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.controller;

import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mockStatic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ConsoleRedirectControllerTest {
    
    private MockMvc mockMvc;
    
    private MockedStatic<EnvUtil> mockedEnvUtil;
    
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ConsoleRedirectController()).build();
        mockedEnvUtil = mockStatic(EnvUtil.class);
    }
    
    @AfterEach
    void tearDown() {
        mockedEnvUtil.close();
    }
    
    @Test
    void testIndexRedirectsToNextByDefault() throws Exception {
        mockedEnvUtil.when(() -> EnvUtil.getProperty("nacos.console.ui.default", "next"))
            .thenReturn("next");
        mockMvc.perform(get("/")).andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/next/"));
    }
    
    @Test
    void testIndexRedirectsToLegacyWhenConfigured() throws Exception {
        mockedEnvUtil.when(() -> EnvUtil.getProperty("nacos.console.ui.default", "next"))
            .thenReturn("legacy");
        mockMvc.perform(get("/")).andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/legacy/"));
    }
    
    @Test
    void testNextForwardsToIndexHtml() throws Exception {
        mockMvc.perform(get("/next/")).andExpect(status().isOk())
            .andExpect(forwardedUrl("/next/index.html"));
    }
    
    @Test
    void testLegacyForwardsToIndexHtml() throws Exception {
        mockMvc.perform(get("/legacy/")).andExpect(status().isOk())
            .andExpect(forwardedUrl("/legacy/index.html"));
    }
}
