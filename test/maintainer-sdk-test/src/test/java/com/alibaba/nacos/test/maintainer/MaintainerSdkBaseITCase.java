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

package com.alibaba.nacos.test.maintainer;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.maintainer.client.NacosMaintainerFactory;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerFactory;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import com.alibaba.nacos.maintainer.client.config.ConfigMaintainerService;
import com.alibaba.nacos.maintainer.client.naming.NamingMaintainerFactory;
import com.alibaba.nacos.maintainer.client.naming.NamingMaintainerService;
import org.junit.jupiter.api.AfterEach;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Shared standalone-server maintainer SDK integration test infrastructure.
 *
 * @author xiweng.yy
 */
public abstract class MaintainerSdkBaseITCase {
    
    protected static final String NACOS_HOST = System.getProperty("nacos.host", "127.0.0.1");
    
    protected static final String NACOS_PORT = System.getProperty("nacos.port", "8848");
    
    protected static final String NACOS_CONTEXT_PATH = System.getProperty("nacos.contextPath",
            "/nacos");

    protected static final String SERVER_ADDR = NACOS_HOST + ":" + NACOS_PORT;
    
    private final Deque<CleanupAction> cleanupActions = new ArrayDeque<>();
    
    private final Deque<CleanupAction> shutdownActions = new ArrayDeque<>();
    
    @AfterEach
    public void tearDownMaintainerSdkBase() throws Exception {
        Exception failure = runActions(cleanupActions, null);
        failure = runActions(shutdownActions, failure);
        if (null != failure) {
            throw failure;
        }
    }
    
    protected ConfigMaintainerService createConfigMaintainerService() throws NacosException {
        ConfigMaintainerService service =
                NacosMaintainerFactory.createConfigMaintainerService(maintainerProperties());
        shutdownActions.addFirst(service::shutdown);
        return service;
    }

    protected NamingMaintainerService createNamingMaintainerService() throws NacosException {
        NamingMaintainerService service =
                NamingMaintainerFactory.createNamingMaintainerService(maintainerProperties());
        shutdownActions.addFirst(service::shutdown);
        return service;
    }
    
    protected AiMaintainerService createAiMaintainerService() throws NacosException {
        return AiMaintainerFactory.createAiMaintainerService(maintainerProperties());
    }
    
    protected Properties maintainerProperties() {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, SERVER_ADDR);
        properties.setProperty(PropertyKeyConst.CONTEXT_PATH, NACOS_CONTEXT_PATH);
        return properties;
    }
    
    protected void addCleanup(CleanupAction cleanupAction) {
        cleanupActions.addFirst(cleanupAction);
    }
    
    protected String randomMaintainerName(String scenario) {
        return "maintainer-sdk-it-" + scenario + "-" + randomSuffix();
    }
    
    protected String randomDataId(String scenario) {
        return randomMaintainerName(scenario) + ".data";
    }
    
    protected String randomGroup(String scenario) {
        return ("MAINTAINER_SDK_IT_" + scenario + "_" + randomSuffix()).toUpperCase(Locale.ROOT);
    }
    
    protected void waitUntil(String reason, CheckedCondition condition) throws Exception {
        long deadline = System.currentTimeMillis() + 10000;
        Throwable lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (condition.evaluate()) {
                    return;
                }
            } catch (Throwable throwable) {
                lastFailure = throwable;
            }
            Thread.sleep(500);
        }
        if (null == lastFailure) {
            fail(reason);
        }
        fail(reason + ", last failure: " + lastFailure.getMessage(), lastFailure);
    }
    
    private String randomSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
    
    private Exception runActions(Deque<CleanupAction> actions, Exception failure) {
        Exception result = failure;
        while (!actions.isEmpty()) {
            try {
                actions.removeFirst().run();
            } catch (Exception exception) {
                if (null == result && !isCleanupIgnorable(exception)) {
                    result = exception;
                } else if (null != result) {
                    result.addSuppressed(exception);
                }
            }
        }
        return result;
    }
    
    private boolean isCleanupIgnorable(Exception exception) {
        if (!(exception instanceof NacosException)) {
            return false;
        }
        NacosException nacosException = (NacosException) exception;
        String message = String.valueOf(nacosException.getMessage()).toLowerCase(Locale.ROOT);
        return NacosException.NOT_FOUND == nacosException.getErrCode()
                || NacosException.RESOURCE_NOT_FOUND == nacosException.getErrCode()
                || message.contains("not found") || message.contains("not exist");
    }
    
    @FunctionalInterface
    protected interface CleanupAction {
        
        void run() throws Exception;
    }
    
    @FunctionalInterface
    protected interface CheckedCondition {
        
        boolean evaluate() throws Exception;
    }
}
