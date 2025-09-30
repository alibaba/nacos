/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.service;

import com.alibaba.nacos.config.server.model.form.ConfigForm;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * The simple implementation of {@link SyncEffectService}. Implemented by direct waiting time.
 *
 * <p>
 *     This implementation is a very simple one. The best expected one is listen
 *     {@link com.alibaba.nacos.config.server.model.event.LocalDataChangeEvent} and then do async to sync by
 *     {@link java.util.concurrent.CountDownLatch} or {@link java.util.concurrent.Future}
 * </p>
 *
 * @author xiweng.yy
 */
@SuppressWarnings("PMD.ServiceOrDaoClassShouldEndWithImplRule")
@Service
public class SimpleSyncEffectService implements SyncEffectService {
    
    @Override
    public void toSync(ConfigForm configForm, long startTimeStamp, long timeout, TimeUnit timeUnit) {
        try {
            Thread.sleep(timeout);
        } catch (InterruptedException ignored) {
        }
    }
}
