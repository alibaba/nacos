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

package com.alibaba.nacos.core.distributed.distro.core;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class TimeWeightedRetryPolicy extends AbstractRetryPolicy {

    private static final long BASE_DELAY_TIME_MS = 2000;
    private static final int MAXIMUM_COEFFICIENT = 4;

    @Override
    public void retryTask(SyncTask syncTask) {
        dataSyncer.submit(syncTask, culDelayTime(syncTask.getRetryCount()));
    }

    @Override
    public String name() {
        return "time-weighted";
    }

    // ln(retryCnt) * BASE_DELAY_TIME_MS

    private long culDelayTime(int retryCnt) {
        return Math.min(MAXIMUM_COEFFICIENT, Math.round(Math.sqrt(retryCnt))) * BASE_DELAY_TIME_MS;
    }

}
