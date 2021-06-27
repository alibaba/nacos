/*
 *   Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.alibaba.nacos.client.identify;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.ScheduledExecutorService;

public class CredentialWatcherTest {
    
    @Test
    public void stop() throws NoSuchFieldException, IllegalAccessException {
        CredentialService instance =  CredentialService.getInstance();
        CredentialWatcher watcher = new CredentialWatcher("app", instance);
        watcher.stop();
        Field executorField = CredentialWatcher.class.getDeclaredField("executor");
        executorField.setAccessible(true);
        ScheduledExecutorService executor = (ScheduledExecutorService) executorField.get(watcher);
        Assert.assertTrue(executor.isShutdown());
    }
}