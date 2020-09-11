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

package com.alibaba.nacos.naming.cluster.remote.response;

import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.core.distributed.distro.entity.DistroData;

/**
 * Distro data response.
 *
 * @author xiweng.yy
 */
public class DistroDataResponse extends Response {
    
    private DistroData distroData;
    
    public DistroData getDistroData() {
        return distroData;
    }
    
    public void setDistroData(DistroData distroData) {
        this.distroData = distroData;
    }
}
