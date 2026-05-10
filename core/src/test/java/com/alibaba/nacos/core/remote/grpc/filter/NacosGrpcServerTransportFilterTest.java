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

package com.alibaba.nacos.core.remote.grpc.filter;

import io.grpc.Attributes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NacosGrpcServerTransportFilterTest {
    
    @Test
    void testConstants() {
        assertEquals("SDK", NacosGrpcServerTransportFilter.SDK_FILTER);
        assertEquals("CLUSTER", NacosGrpcServerTransportFilter.CLUSTER_FILTER);
    }
    
    @Test
    void testConcreteFilterTypeAndTransportReady() {
        NacosGrpcServerTransportFilter filter = new NacosGrpcServerTransportFilter() {
            
            @Override
            public String type() {
                return "SDK";
            }
        };
        assertEquals("SDK", filter.type());
        Attributes attrs = Attributes.newBuilder().build();
        assertNotNull(filter.transportReady(attrs));
    }
}
