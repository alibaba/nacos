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
 * discovery java spi
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public interface RetryPolicy {

    /**
     * inject {@link DataSyncer}
     *
     * @param dataSyncer {@link DataSyncer}
     */
    void injectDataSyncer(DataSyncer dataSyncer);

    /**
     * Retry the task
     *
     * @param syncTask {@link SyncTask}
     */
    void retryTask(SyncTask syncTask);

    /**
     * Strategy name
     *
     * @return String
     */
    String name();

}
