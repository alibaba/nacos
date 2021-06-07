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

import com.alibaba.nacos.client.utils.LogUtils;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.RateLimiter;
import org.slf4j.Logger;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Limiter.
 *
 * @author Nacos
 */
public class Limiter {
    
    private static final Logger LOGGER = LogUtils.logger(Limiter.class);
    
    private static final int CAPACITY_SIZE = 1000;
    
    private static final int LIMIT_TIME = 1000;
    
    private static final Cache<String, RateLimiter> CACHE = CacheBuilder.newBuilder().initialCapacity(CAPACITY_SIZE)
            .expireAfterAccess(1, TimeUnit.MINUTES).build();
    
    private static final String LIMIT_TIME_PROPERTY = "limitTime";
    
    /**
     * qps 5.
     */
    private static double limit = 5;
    
    static {
        try {
            String limitTimeStr = System.getProperty(LIMIT_TIME_PROPERTY, String.valueOf(limit));
            limit = Double.parseDouble(limitTimeStr);
            LOGGER.info("limitTime:{}", limit);
        } catch (Exception e) {
            LOGGER.error("init limitTime fail", e);
        }
    }
    
    /**
     * Judge whether access key is limited.
     *
     * @param accessKeyID access key
     * @return true if is limited, otherwise false
     */
    public static boolean isLimit(String accessKeyID) {
        RateLimiter rateLimiter = null;
        try {
            rateLimiter = CACHE.get(accessKeyID, new Callable<RateLimiter>() {
                @Override
                public RateLimiter call() throws Exception {
                    return RateLimiter.create(limit);
                }
            });
        } catch (ExecutionException e) {
            LOGGER.error("create limit fail", e);
        }
        if (rateLimiter != null && !rateLimiter.tryAcquire(LIMIT_TIME, TimeUnit.MILLISECONDS)) {
            LOGGER.error("access_key_id:{} limited", accessKeyID);
            return true;
        }
        return false;
    }
    
}
