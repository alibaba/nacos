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
 * Server running mode
 * <p>
 * We use CAP theory to set the server mode, users can select their preferred mode in running time.
 * <p>
 * CP mode provides strong consistency, data persistence but network partition tolerance.
 * <p>
 * AP mode provides eventual consistency and network partition tolerance but data persistence.
 * <p>
 * Mixed mode provides CP for some data and AP for some other data.
 * <p>
 * Service level information and cluster level information are always operated via CP protocol, so
 * in AP mode they cannot be edited.
 *
 * @author nkorange
 * @since 1.0.0
 */
public enum ServerMode {
    /**
     * AP mode
     */
    AP,
    /**
     * CP mode
     */
    CP,
    /**
     * Mixed mode
     */
    MIXED
}
