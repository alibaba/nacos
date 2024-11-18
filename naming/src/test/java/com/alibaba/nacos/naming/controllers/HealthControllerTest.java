/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.naming.controllers;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.naming.constants.RequestConstant;
import com.alibaba.nacos.naming.core.HealthOperatorV2Impl;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link HealthController} unit test.
 *
 * @author chenglu
 * @date 2021-07-21 19:19
 */
@ExtendWith(MockitoExtension.class)
class HealthControllerTest {
    
    @InjectMocks
    private HealthController healthController;
    
    @Mock
    private HealthOperatorV2Impl healthOperatorV2;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new MockEnvironment());
    }
    
    @Test
    void testServer() {
        ResponseEntity responseEntity = healthController.server();
        assertEquals(200, responseEntity.getStatusCodeValue());
    }
    
    @Test
    void testUpdate() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addParameter(CommonParams.SERVICE_NAME, "test");
        servletRequest.addParameter(RequestConstant.IP_KEY, "1.1.1.1");
        servletRequest.addParameter(RequestConstant.PORT_KEY, "8848");
        servletRequest.addParameter(RequestConstant.HEALTHY_KEY, "true");
        
        try {
            ResponseEntity responseEntity = healthController.update(servletRequest);
            assertEquals(200, responseEntity.getStatusCodeValue());
        } catch (NacosException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
    @Test
    void testCheckers() {
        ResponseEntity responseEntity = healthController.checkers();
        assertEquals(200, responseEntity.getStatusCodeValue());
    }
}
