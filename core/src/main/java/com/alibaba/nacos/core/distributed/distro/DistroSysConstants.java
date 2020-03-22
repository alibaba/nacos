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

package com.alibaba.nacos.core.distributed.distro;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class DistroSysConstants {

    // ======= default value ======= //

    /**
     * {@link DistroSysConstants#TASK_DISPATCH_PERIOD_MS}
     */
    public static final long DEFAULT_TASK_DISPATCH_PERIOD = 2000;

    /**
     * {@link DistroSysConstants#BATCH_SYNC_KEY_COUNT}
     */
    public static final int DEFAULT_BATCH_SYNC_KEY_COUNT = 1000;

    /**
     * {@link DistroSysConstants#SYNC_RETRY_DELAY_MS}
     */
    public static final long DEFAULT_SYNC_RETRY_DELAY_MS = 5000;

    /**
     * {@link DistroSysConstants#DISTRO_ENABLED}
     */
    public static final boolean DEFAULT_DISTRO_ENABLED = true;

    /**
     * {@link DistroSysConstants#RETRY_SYNC_POLICY}
     */
    public static final String DEFAULT_RETRY_SYNC_POLICY = "simple";

    // ======= setting key ======= //

    public static final String WEB_CONTEXT_PATH = "context_path";

    /**
     * Maximum interval between two data transmissions
     */
    public static final String TASK_DISPATCH_PERIOD_MS = "task_dispatch_period_ms";

    /**
     * Number of keys per batch of tasks
     */
    public static final String BATCH_SYNC_KEY_COUNT = "batch_sync_key_count";

    /**
     * Task retry delay time
     */
    public static final String SYNC_RETRY_DELAY_MS = "sync_retry_delay_ms";

    /**
     * Whether to enable the authoritative server mechanism
     */
    public static final String DISTRO_ENABLED = "distro_enabled";

    /**
     * Data synchronization retry strategy
     */
    public static final String RETRY_SYNC_POLICY = "retry_policy";

}
