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

package com.alibaba.nacos.naming.cluster;

/**
 * A flag to indicate the exact status of a server.
 *
 * @author nkorange
 * @since 1.0.0
 */
public enum ServerStatus {
    /**
     * server is up and ready for request.
     */
    UP,
    /**
     * server is out of service, something abnormal happened.
     */
    DOWN,
    /**
     * server is preparing itself for request, usually 'UP' is the next status.
     */
    STARTING,
    /**
     * server is manually paused.
     */
    PAUSED,
    /**
     * only write operation is permitted.
     */
    WRITE_ONLY,
    /**
     * only read operation is permitted.
     */
    READ_ONLY
}
