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

package com.alibaba.nacos.test.sdk;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.AiFactory;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.config.ConfigFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.lock.LockService;
import com.alibaba.nacos.api.lock.NacosLockFactory;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import org.junit.jupiter.api.AfterEach;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Shared standalone-server Java SDK integration test infrastructure.
 *
 * @author xiweng.yy
 */
public abstract class JavaSdkBaseITCase {
    
    protected static final String NACOS_HOST = System.getProperty("nacos.host", "127.0.0.1");
    
    protected static final String NACOS_PORT = System.getProperty("nacos.port", "8848");
    
    protected static final String SERVER_ADDR = NACOS_HOST + ":" + NACOS_PORT;
    
    protected static final int DEFAULT_TIMEOUT_MS = 3000;
    
    private static final String SDK_STATUS_UP = "UP";
    
    private final Deque<CleanupAction> cleanupActions = new ArrayDeque<>();
    
    private final Deque<CleanupAction> shutdownActions = new ArrayDeque<>();
    
    @AfterEach
    public void tearDownJavaSdkBase() throws Exception {
        Exception failure = runActions(cleanupActions, null);
        failure = runActions(shutdownActions, failure);
        if (null != failure) {
            throw failure;
        }
    }
    
    protected ConfigService createConfigService() throws Exception {
        ConfigService service = ConfigFactory.createConfigService(sdkProperties());
        shutdownActions.addFirst(service::shutDown);
        waitUntil("config SDK client should connect to server",
                () -> SDK_STATUS_UP.equals(service.getServerStatus()));
        return service;
    }
    
    protected NamingService createNamingService() throws Exception {
        NamingService service = NamingFactory.createNamingService(sdkProperties());
        shutdownActions.addFirst(service::shutDown);
        waitUntil("naming SDK client should connect to server",
                () -> SDK_STATUS_UP.equals(service.getServerStatus()));
        return service;
    }
    
    protected AiService createAiService() throws NacosException {
        AiService service = AiFactory.createAiService(sdkProperties());
        shutdownActions.addFirst(service::shutdown);
        return service;
    }
    
    protected LockService createLockService() throws NacosException {
        LockService service = NacosLockFactory.createLockService(sdkProperties());
        shutdownActions.addFirst(service::shutdown);
        return service;
    }
    
    protected Properties sdkProperties() {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, SERVER_ADDR);
        return properties;
    }
    
    protected void addCleanup(CleanupAction cleanupAction) {
        cleanupActions.addFirst(cleanupAction);
    }
    
    protected String randomDataId(String scenario) {
        return "java-sdk-it-" + scenario + "-" + randomSuffix() + ".data";
    }
    
    protected String randomGroup(String scenario) {
        return "JAVA_SDK_IT_" + scenario.toUpperCase(Locale.ROOT) + "_"
                + randomSuffix().toUpperCase(Locale.ROOT);
    }
    
    protected String randomServiceName(String scenario) {
        return "java-sdk-it-" + scenario + "-" + randomSuffix();
    }
    
    protected int randomPort() {
        return 10000 + Math.abs(UUID.randomUUID().hashCode() % 30000);
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
        return NacosException.NOT_FOUND == nacosException.getErrCode()
                || NacosException.RESOURCE_NOT_FOUND == nacosException.getErrCode();
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
