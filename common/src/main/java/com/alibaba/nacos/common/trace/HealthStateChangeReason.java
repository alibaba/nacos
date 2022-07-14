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

package com.alibaba.nacos.common.trace;

/**
 * The reasons of health state change.
 *
 * @author yanda
 */

public enum HealthStateChangeReason {
    /**
     * Instance heart beat timeout.
     */
    HEARTBEAT_TIMEOUT,
    /**
     * Instance heart beat refresh.
     */
    HEARTBEAT_REFRESH,
    /**
     * Instance health check fail.
     */
    HEALTH_CHECK_FAIL,
    /**
     * Instance health check success.
     */
    HEALTH_CHECK_SUCCESS;
}
