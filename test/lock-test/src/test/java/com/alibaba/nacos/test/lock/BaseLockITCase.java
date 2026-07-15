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

package com.alibaba.nacos.test.lock;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.lock.LockService;
import com.alibaba.nacos.api.lock.common.LockConstants;
import com.alibaba.nacos.api.lock.model.LockInstance;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.core.env.StandardEnvironment;

import java.util.Properties;
import java.util.UUID;

/**
 * 分布式锁集成测试基类.
 *
 * <p>连接外部已启动的 Nacos 服务器并提供锁服务客户端.
 *
 * @author DHX
 * @date 2026/05/30
 */
public abstract class BaseLockITCase {

    protected static final String SERVER_ADDR = "127.0.0.1:8848";

    protected static LockService lockService;

    @BeforeAll
    static void beforeAll() throws Exception {
        EnvUtil.setEnvironment(new StandardEnvironment());

        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, SERVER_ADDR);
        properties.setProperty(PropertyKeyConst.USERNAME, "nacos");
        properties.setProperty(PropertyKeyConst.PASSWORD, "nacos");

        lockService = NacosFactory.createLockService(properties);
    }

    @AfterAll
    static void afterAll() throws Exception {
        if (lockService != null) {
            lockService.shutdown();
        }
    }

    protected LockInstance createReentrantLock(String key) {
        return createLockInstance(key, LockConstants.REENTRANT_LOCK_TYPE);
    }

    protected LockInstance createNonReentrantLock(String key) {
        return createLockInstance(key, LockConstants.NON_REENTRANT_LOCK_TYPE);
    }

    protected LockInstance createLockInstance(String key, String lockType) {
        LockInstance instance = new LockInstance();
        instance.setKey(key);
        instance.setLockType(lockType);
        instance.setOwner(UUID.randomUUID().toString());
        instance.setExpiredTime(30000L);
        return instance;
    }

    protected String generateUniqueKey(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
