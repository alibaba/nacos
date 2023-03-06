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

package com.alibaba.nacos.core.distributed.distro.component;

import com.alibaba.nacos.core.distributed.distro.entity.DistroData;
import com.alibaba.nacos.core.distributed.distro.entity.DistroKey;

/**
 * Distro transport agent.
 *
 * @author xiweng.yy
 */
public interface DistroTransportAgent {
    
    /**
     * Whether support transport data with callback.
     *
     * @return true if support, otherwise false
     */
    boolean supportCallbackTransport();
    
    /**
     * Sync data.
     *
     * @param data         data
     * @param targetServer target server
     * @return true is sync successfully, otherwise false
     */
    boolean syncData(DistroData data, String targetServer);
    
    /**
     * Sync data with callback.
     *
     * @param data         data
     * @param targetServer target server
     * @param callback     callback
     * @throws UnsupportedOperationException if method supportCallbackTransport is false, should throw {@code
     *                                       UnsupportedOperationException}
     */
    void syncData(DistroData data, String targetServer, DistroCallback callback);
    
    /**
     * Sync verify data.
     *
     * @param verifyData   verify data
     * @param targetServer target server
     * @return true is verify successfully, otherwise false
     */
    boolean syncVerifyData(DistroData verifyData, String targetServer);
    
    /**
     * Sync verify data.
     *
     * @param verifyData   verify data
     * @param targetServer target server
     * @param callback     callback
     * @throws UnsupportedOperationException if method supportCallbackTransport is false, should throw {@code
     *                                       UnsupportedOperationException}
     */
    void syncVerifyData(DistroData verifyData, String targetServer, DistroCallback callback);
    
    /**
     * get Data from target server.
     *
     * @param key          key of data
     * @param targetServer target server
     * @return distro data
     */
    DistroData getData(DistroKey key, String targetServer);
    
    /**
     * Get all datum snapshot from target server.
     *
     * @param targetServer target server.
     * @return distro data
     */
    DistroData getDatumSnapshot(String targetServer);
}
