/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.interceptor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractNamingInterceptorChainTest {
    
    @Test
    void testAddInterceptorSortsAndSkipsUnsupportedInterceptor() {
        TestChain chain = new TestChain();
        TestInterceptor last = new TestInterceptor(10, true, false);
        TestInterceptor skipped = new TestInterceptor(1, false, true);
        chain.addInterceptor(last);
        chain.addInterceptor(skipped);
        
        assertEquals(skipped, chain.interceptors().get(0));
        assertEquals(last, chain.interceptors().get(1));
        
        TestInterceptable object = new TestInterceptable();
        chain.doInterceptor(object);
        
        assertFalse(skipped.intercepted);
        assertTrue(last.intercepted);
        assertTrue(object.passed);
        assertFalse(object.afterIntercepted);
    }
    
    @Test
    void testDoInterceptorStopsWhenIntercepted() {
        TestChain chain = new TestChain();
        TestInterceptor first = new TestInterceptor(1, true, true);
        TestInterceptor second = new TestInterceptor(2, true, false);
        chain.addInterceptor(first);
        chain.addInterceptor(second);
        
        TestInterceptable object = new TestInterceptable();
        chain.doInterceptor(object);
        
        assertTrue(first.intercepted);
        assertFalse(second.intercepted);
        assertFalse(object.passed);
        assertTrue(object.afterIntercepted);
    }
    
    private static class TestChain extends AbstractNamingInterceptorChain<TestInterceptable> {
        
        @SuppressWarnings({"unchecked", "rawtypes"})
        TestChain() {
            super((Class) NacosNamingInterceptor.class);
        }
        
        List<NacosNamingInterceptor<TestInterceptable>> interceptors() {
            return getInterceptors();
        }
    }
    
    private static class TestInterceptable implements Interceptable {
        
        private boolean passed;
        
        private boolean afterIntercepted;
        
        @Override
        public void passIntercept() {
            passed = true;
        }
        
        @Override
        public void afterIntercept() {
            afterIntercepted = true;
        }
    }
    
    private static class TestInterceptor implements NacosNamingInterceptor<TestInterceptable> {
        
        private final int order;
        
        private final boolean supportType;
        
        private final boolean interceptResult;
        
        private boolean intercepted;
        
        TestInterceptor(int order, boolean supportType, boolean interceptResult) {
            this.order = order;
            this.supportType = supportType;
            this.interceptResult = interceptResult;
        }
        
        @Override
        public boolean isInterceptType(Class<?> type) {
            return supportType;
        }
        
        @Override
        public boolean intercept(TestInterceptable object) {
            intercepted = true;
            return interceptResult;
        }
        
        @Override
        public int order() {
            return order;
        }
    }
}
