/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.remote.grpc.negotiator.tls;

import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.ConfigurableEnvironment;


/**
 * Test RpcServerSslContextRefresherHolder.
 *
 * @author stone-98
 */
@ExtendWith(MockitoExtension.class)
class RpcServerSslContextRefresherHolderTest {
    
    @Mock
    private ConfigurableEnvironment environment;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(environment);
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    void testInit() {
    }
    
}
