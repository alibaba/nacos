/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.auth.serveridentity;

import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ServerIdentityCheckerHolderTest {
    
    Map<Class<?>, Collection<Class<?>>> servicesMap;
    
    @BeforeEach
    void setUp() {
        servicesMap = (Map<Class<?>, Collection<Class<?>>>) ReflectionTestUtils.getField(NacosServiceLoader.class,
                "SERVICES");
    }
    
    @AfterEach
    void tearDown() {
        servicesMap.remove(ServerIdentityChecker.class);
    }
    
    @Test
    void testConstructorWithSingleImplementation()
            throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        ServerIdentityCheckerHolder holder = getNewHolder(1);
        assertInstanceOf(MockChecker.class, holder.getChecker());
    }
    
    @Test
    void testConstructorWithMultipleImplementation()
            throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        ServerIdentityCheckerHolder holder = getNewHolder(2);
        assertInstanceOf(MockChecker.class, holder.getChecker());
    }
    
    ServerIdentityCheckerHolder getNewHolder(int size)
            throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        List<Class<?>> classes = new LinkedList<>();
        for (int i = 0; i < size; i++) {
            classes.add(MockChecker.class);
        }
        servicesMap.put(ServerIdentityChecker.class, classes);
        Constructor<ServerIdentityCheckerHolder> constructor = ServerIdentityCheckerHolder.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }
    
    public static class MockChecker implements ServerIdentityChecker {
        
        @Override
        public void init(AuthConfigs authConfigs) {
        }
        
        @Override
        public ServerIdentityResult check(ServerIdentity serverIdentity, Secured secured) {
            return ServerIdentityResult.success();
        }
    }
}