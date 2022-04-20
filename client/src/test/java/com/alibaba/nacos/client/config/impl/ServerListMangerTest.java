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

package com.alibaba.nacos.client.config.impl;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ServerListMangerTest {

    private static final Logger LOG = LoggerFactory.getLogger(ServerListMangerTest.class);

    /**
     * Tests for https://github.com/alibaba/nacos/issues/8203.
     */
    @Test
    public void testGetCurrentServerAddr() {

        int concurrency = 10;
        int maxAttempt = 3;

        final AtomicBoolean failed = new AtomicBoolean(false);

        ExecutorService executorService = new ThreadPoolExecutor(
                concurrency, concurrency, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

        for (int i = 0; i < maxAttempt; i++) {
            final int attempt = i;

            // create a new ServerListManager instance to make the currentServerAddr field not initialized
            final ServerListManager serverListManager = new ServerListManager(Arrays.asList("1.2.3.4:8848"));

            final CountDownLatch latch = new CountDownLatch(concurrency);

            // imitate the situation that ServerHttpAgent.httpGet() is called concurrently
            for (int j = 0; j < concurrency; j++) {
                executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (failed.get()) {
                            return;
                        }

                        try {
                            serverListManager.getCurrentServerAddr();
                        } catch (NoSuchElementException e) {
                            failed.set(true);
                            LOG.error("failed at " + attempt + " attempt", e);
                        } finally {
                            latch.countDown();
                        }
                    }
                });
            }

            try {
                latch.await(1000, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                LOG.error("await error", e);
            }

            if (failed.get()) {
                break;
            }
        }

        executorService.shutdown();

        Assert.assertFalse(failed.get());
    }

}
