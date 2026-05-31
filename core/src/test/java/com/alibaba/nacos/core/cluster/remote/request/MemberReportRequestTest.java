/*
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
 */

package com.alibaba.nacos.core.cluster.remote.request;

import com.alibaba.nacos.api.common.NodeState;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MemberReportRequestTest {
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new MockEnvironment());
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(null);
    }
    
    @Test
    void defaultConstructor() {
        MemberReportRequest request = new MemberReportRequest();
        assertNull(request.getNode());
    }
    
    @Test
    void constructorWithNode() {
        Member node = Member.builder().ip("127.0.0.1").port(8848).state(NodeState.UP).build();
        MemberReportRequest request = new MemberReportRequest(node);
        assertEquals(node, request.getNode());
    }
    
    @Test
    void setNode() {
        MemberReportRequest request = new MemberReportRequest();
        Member node = Member.builder().ip("192.168.1.1").port(8848).state(NodeState.UP).build();
        request.setNode(node);
        assertEquals(node, request.getNode());
    }
}
