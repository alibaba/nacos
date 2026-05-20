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

package com.alibaba.nacos.plugin.auth.impl.persistence.handler;

import com.alibaba.nacos.plugin.auth.impl.persistence.handler.support.DefaultPageHandlerAdapter;
import com.alibaba.nacos.plugin.auth.impl.persistence.handler.support.DerbyPageHandlerAdapter;
import com.alibaba.nacos.plugin.auth.impl.persistence.handler.support.MysqlPageHandlerAdapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageHandlerAdapterFactoryTest {
    
    @Test
    void testGetInstanceReturnsSingletonWithDefaultAdapters() {
        PageHandlerAdapterFactory factory = PageHandlerAdapterFactory.getInstance();
        
        assertSame(factory, PageHandlerAdapterFactory.getInstance());
        assertEquals(3, factory.getHandlerAdapters().size());
        assertTrue(factory.getHandlerAdapters().get(0) instanceof MysqlPageHandlerAdapter);
        assertTrue(factory.getHandlerAdapters().get(1) instanceof DerbyPageHandlerAdapter);
        assertTrue(factory.getHandlerAdapters().get(2) instanceof DefaultPageHandlerAdapter);
        assertEquals(3, factory.getHandlerAdapterMap().size());
        assertTrue(factory.getHandlerAdapterMap()
            .get(MysqlPageHandlerAdapter.class.getName()) instanceof MysqlPageHandlerAdapter);
        assertTrue(factory.getHandlerAdapterMap()
            .get(DerbyPageHandlerAdapter.class.getName()) instanceof DerbyPageHandlerAdapter);
        assertTrue(factory.getHandlerAdapterMap()
            .get(DefaultPageHandlerAdapter.class.getName()) instanceof DefaultPageHandlerAdapter);
    }
    
    @Test
    void testCollectionsAreUnmodifiable() {
        PageHandlerAdapterFactory factory = PageHandlerAdapterFactory.getInstance();
        
        assertThrows(UnsupportedOperationException.class,
            () -> factory.getHandlerAdapters().add(new DefaultPageHandlerAdapter()));
        assertThrows(UnsupportedOperationException.class,
            () -> factory.getHandlerAdapterMap().clear());
    }
}
