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

import java.util.List;

/**
 * Distro data storage.
 *
 * @author xiweng.yy
 */
public interface DistroDataStorage {
    
    /**
     * Set this distro data storage has finished initial step.
     */
    void finishInitial();
    
    /**
     * Whether this distro data is finished initial.
     *
     * <p>If not finished, this data storage should not send verify data to other node.
     *
     * @return {@code true} if finished, otherwise false
     */
    boolean isFinishInitial();
    
    /**
     * Get distro datum.
     *
     * @param distroKey key of distro datum
     * @return need to sync datum
     */
    DistroData getDistroData(DistroKey distroKey);
    
    /**
     * Get all distro datum snapshot.
     *
     * @return all datum
     */
    DistroData getDatumSnapshot();
    
    /**
     * Get verify datum.
     *
     * @return verify datum
     */
    List<DistroData> getVerifyData();
}
