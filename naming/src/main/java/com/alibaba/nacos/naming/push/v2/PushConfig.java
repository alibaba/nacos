/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.push.v2;

import com.alibaba.nacos.core.config.AbstractDynamicConfig;
import com.alibaba.nacos.naming.constants.PushConstants;
import com.alibaba.nacos.sys.env.EnvUtil;

/**
 * Push configuration.
 *
 * @author xiweng.yy
 */
public class PushConfig extends AbstractDynamicConfig {
    
    private static final String PUSH = "Push";
    
    private static final PushConfig INSTANCE = new PushConfig();
    
    private long pushTaskDelay = PushConstants.DEFAULT_PUSH_TASK_DELAY;
    
    private long pushTaskTimeout = PushConstants.DEFAULT_PUSH_TASK_TIMEOUT;
    
    private long pushTaskRetryDelay = PushConstants.DEFAULT_PUSH_TASK_RETRY_DELAY;
    
    private PushConfig() {
        super(PUSH);
        resetConfig();
    }
    
    @Override
    protected void getConfigFromEnv() {
        pushTaskDelay = EnvUtil
                .getProperty(PushConstants.PUSH_TASK_DELAY, Long.class, PushConstants.DEFAULT_PUSH_TASK_DELAY);
        pushTaskTimeout = EnvUtil
                .getProperty(PushConstants.PUSH_TASK_TIMEOUT, Long.class, PushConstants.DEFAULT_PUSH_TASK_TIMEOUT);
        pushTaskRetryDelay = EnvUtil.getProperty(PushConstants.PUSH_TASK_RETRY_DELAY, Long.class,
                PushConstants.DEFAULT_PUSH_TASK_RETRY_DELAY);
    }
    
    @Override
    protected String printConfig() {
        return "PushConfig{" + "pushTaskDelay=" + pushTaskDelay + ", pushTaskTimeout=" + pushTaskTimeout
                + ", pushTaskRetryDelay=" + pushTaskRetryDelay + '}';
    }
    
    public static PushConfig getInstance() {
        return INSTANCE;
    }
    
    public long getPushTaskDelay() {
        return pushTaskDelay;
    }
    
    public long getPushTaskTimeout() {
        return pushTaskTimeout;
    }
    
    public long getPushTaskRetryDelay() {
        return pushTaskRetryDelay;
    }
}
