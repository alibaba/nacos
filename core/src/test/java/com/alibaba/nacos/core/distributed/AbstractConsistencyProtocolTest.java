/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.distributed;

import com.alibaba.nacos.consistency.Config;
import com.alibaba.nacos.consistency.ProtocolMetaData;
import com.alibaba.nacos.consistency.RequestProcessor;
import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AbstractConsistencyProtocol} unit test.
 */
class AbstractConsistencyProtocolTest {
    
    @Test
    void testLoadLogProcessorAndProtocolMetaData() {
        TestConsistencyProtocol protocol = new TestConsistencyProtocol();
        TestRequestProcessor p1 = new TestRequestProcessor("group1");
        TestRequestProcessor p2 = new TestRequestProcessor("group2");
        
        protocol.loadLogProcessor(java.util.Arrays.asList(p1, p2));
        
        assertEquals(2, protocol.getProcessorCount());
        assertTrue(protocol.hasProcessor("group1"));
        assertTrue(protocol.hasProcessor("group2"));
        ProtocolMetaData metaData = protocol.protocolMetaData();
        assertNotNull(metaData);
        assertSame(metaData, protocol.protocolMetaData());
    }
    
    @Test
    void testLoadLogProcessorOverwriteSameGroup() {
        TestConsistencyProtocol protocol = new TestConsistencyProtocol();
        TestRequestProcessor p1 = new TestRequestProcessor("g");
        TestRequestProcessor p2 = new TestRequestProcessor("g");
        protocol.loadLogProcessor(Collections.singletonList(p1));
        protocol.loadLogProcessor(Collections.singletonList(p2));
        assertEquals(1, protocol.getProcessorCount());
    }
    
    private static class TestConfig implements Config<RequestProcessor> {
        
        private String self;
        private Set<String> members = new HashSet<>();
        
        @Override
        public void setMembers(String self, Set<String> members) {
            this.self = self;
            this.members = members;
        }
        
        @Override
        public void addMembers(Set<String> members) {
            this.members.addAll(members);
        }
        
        @Override
        public void removeMembers(Set<String> members) {
            this.members.removeAll(members);
        }
        
        @Override
        public String getSelfMember() {
            return self;
        }
        
        @Override
        public Set<String> getMembers() {
            return members;
        }
        
        @Override
        public void setVal(String key, String value) {
        }
        
        @Override
        public String getVal(String key) {
            return null;
        }
        
        @Override
        public String getValOfDefault(String key, String defaultVal) {
            return defaultVal;
        }
    }
    
    private static class TestRequestProcessor extends RequestProcessor {
        
        private final String group;
        
        TestRequestProcessor(String group) {
            this.group = group;
        }
        
        @Override
        public Response onRequest(ReadRequest request) {
            return null;
        }
        
        @Override
        public Response onApply(WriteRequest log) {
            return null;
        }
        
        @Override
        public String group() {
            return group;
        }
    }
    
    private static class TestConsistencyProtocol
        extends AbstractConsistencyProtocol<TestConfig, TestRequestProcessor> {
        
        @Override
        public void init(TestConfig config) {
        }
        
        @Override
        public void addRequestProcessors(java.util.Collection<TestRequestProcessor> processors) {
            loadLogProcessor(new java.util.ArrayList<>(processors));
        }
        
        @Override
        public com.alibaba.nacos.consistency.entity.Response getData(ReadRequest request) {
            return null;
        }
        
        @Override
        public CompletableFuture<com.alibaba.nacos.consistency.entity.Response> aGetData(
            ReadRequest request) {
            return CompletableFuture.completedFuture(null);
        }
        
        @Override
        public Response write(WriteRequest request) {
            return null;
        }
        
        @Override
        public CompletableFuture<Response> writeAsync(WriteRequest request) {
            return CompletableFuture.completedFuture(null);
        }
        
        @Override
        public void memberChange(Set<String> addresses) {
        }
        
        @Override
        public boolean isReady() {
            return false;
        }
        
        @Override
        public void shutdown() {
        }
        
        int getProcessorCount() {
            return allProcessor().size();
        }
        
        boolean hasProcessor(String group) {
            return allProcessor().containsKey(group);
        }
    }
}
