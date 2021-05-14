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

package com.alibaba.nacos.naming.cluster.remote.request;

import com.alibaba.nacos.consistency.DataOperation;
import com.alibaba.nacos.core.distributed.distro.entity.DistroData;

/**
 * Distro data request.
 *
 * @author xiweng.yy
 */
public class DistroDataRequest extends AbstractClusterRequest {
    
    private DistroData distroData;
    
    private DataOperation dataOperation;
    
    public DistroDataRequest() {
    }
    
    public DistroDataRequest(DistroData distroData, DataOperation dataOperation) {
        this.distroData = distroData;
        this.dataOperation = dataOperation;
    }
    
    public DistroData getDistroData() {
        return distroData;
    }
    
    public void setDistroData(DistroData distroData) {
        this.distroData = distroData;
    }
    
    public DataOperation getDataOperation() {
        return dataOperation;
    }
    
    public void setDataOperation(DataOperation dataOperation) {
        this.dataOperation = dataOperation;
    }
}
