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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.alibaba.nacos.client.config.utils.LogUtils;
import com.alibaba.nacos.client.logger.Logger;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.RateLimiter;

/**
 * Limiter
 *
 * @author Nacos
 */
public class Limiter {

    static final public Logger log = LogUtils.logger(Limiter.class);
    private static int CAPACITY_SIZE = 1000;
    private static int LIMIT_TIME = 1000;
    private static Cache<String, RateLimiter> cache = CacheBuilder.newBuilder()
        .initialCapacity(CAPACITY_SIZE).expireAfterAccess(1, TimeUnit.MINUTES)
        .build();

    /**
     * qps 5
     */
    private static double limit = 5;

    static {
        try {
            String limitTimeStr = System
                .getProperty("limitTime", String.valueOf(limit));
            limit = Double.parseDouble(limitTimeStr);
            log.info("limitTime:{}", limit);
        } catch (Exception e) {
            log.error("Nacos-xxx", "init limitTime fail", e);
        }
    }

    public static boolean isLimit(String accessKeyID) {
        RateLimiter rateLimiter = null;
        try {
            rateLimiter = cache.get(accessKeyID, new Callable<RateLimiter>() {
                @Override
                public RateLimiter call() throws Exception {
                    return RateLimiter.create(limit);
                }
            });
        } catch (ExecutionException e) {
            log.error("Nacos-XXX", "create limit fail", e);
        }
        if (rateLimiter != null && !rateLimiter.tryAcquire(LIMIT_TIME, TimeUnit.MILLISECONDS)) {
            log.error("Nacos-XXX", "access_key_id:{} limited", accessKeyID);
            return true;
        }
        return false;
    }

}
