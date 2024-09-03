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

package com.alibaba.nacos.client.address.manager;

import com.alibaba.nacos.client.address.common.ModuleType;
import com.alibaba.nacos.common.utils.ReflectUtils;
import com.google.common.collect.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Abstract Server List Manager Test.
 *
 * @author misakacoder
 */
public class AbstractServerListManagerTest {
    
    private AbstractServerListManager abstractServerListManager;
    
    @BeforeEach
    public void setUp() throws Exception {
        abstractServerListManager = new AbstractServerListManager() {
            @Override
            public ModuleType getModuleType() {
                return null;
            }
        };
        ReflectUtils.invokeMethod(getMethod("updateServerList", List.class), abstractServerListManager,
                Arrays.asList("127.0.0.1:8848", "127.0.0.2:8848", "127.0.0.3:8848"));
    }
    
    @Test
    public void testGetNextServer() {
        assertEquals(abstractServerListManager.getNextServer(), "http://127.0.0.2:8848");
    }
    
    @Test
    public void testGetCurrentServer() {
        assertEquals(abstractServerListManager.getCurrentServer(), "http://127.0.0.1:8848");
    }
    
    @Test
    public void testGetServerList() {
        List<String> actual = Arrays.asList("http://127.0.0.1:8848", "http://127.0.0.2:8848", "http://127.0.0.3:8848");
        assertEquals(abstractServerListManager.getServerList(), actual);
    }
    
    @Test
    public void testRepairServerAddr() throws Exception {
        Method method = getMethod("repairServerAddr", String.class);
        Function<String, String> repairServerAddr = p -> ReflectUtils.invokeMethod(method, abstractServerListManager, p)
                .toString();
        assertEquals(repairServerAddr.apply("127.0.0.1"), "http://127.0.0.1:8848");
        assertEquals(repairServerAddr.apply("127.0.0.1:8888"), "http://127.0.0.1:8888");
        assertEquals(repairServerAddr.apply("http://127.0.0.1"), "http://127.0.0.1:8848");
        assertEquals(repairServerAddr.apply("http://127.0.0.1:8888"), "http://127.0.0.1:8888");
        assertEquals(repairServerAddr.apply("https://127.0.0.1"), "https://127.0.0.1:8848");
        assertEquals(repairServerAddr.apply("https://127.0.0.1:8888"), "https://127.0.0.1:8888");
    }
    
    @Test
    public void testUpdateServerList() throws Exception {
        List<String> serverList = Lists.newArrayList("127.0.0.1", "127.0.0.2", "127.0.0.3");
        ReflectUtils.invokeMethod(getMethod("updateServerList", List.class), abstractServerListManager, serverList);
        List<String> actual = Lists.newArrayList("http://127.0.0.1:8848", "http://127.0.0.2:8848",
                "http://127.0.0.3:8848");
        assertEquals(abstractServerListManager.getServerList(), actual);
    }
    
    @Test
    public void testCreateUpdateServerListTask() throws Exception {
        AbstractServerListManager.Supplier<ArrayList<String>> serverListProvider = () -> Lists.newArrayList(
                "127.0.0.1");
        Runnable task = (Runnable) ReflectUtils.invokeMethod(
                getMethod("createUpdateServerListTask", AbstractServerListManager.Supplier.class),
                abstractServerListManager, serverListProvider);
        task.run();
        assertEquals(abstractServerListManager.getServerList(), Lists.newArrayList("http://127.0.0.1:8848"));
    }
    
    private Method getMethod(String methodName, Class<?> parameterTypes) throws Exception {
        Method method = AbstractServerListManager.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method;
    }
}
