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
package com.alibaba.nacos.naming.healthcheck;

/**
 * Health check mode
 *
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
public enum HealthCheckMode {
    /**
     * Health check sent from server.
     */
    server,
    /**
     * Health check sent from client.
     */
    client,
    /**
     * Health check disabled.
     */
    none
}
