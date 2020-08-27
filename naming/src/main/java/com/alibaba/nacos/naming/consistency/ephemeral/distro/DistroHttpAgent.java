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

package com.alibaba.nacos.naming.consistency.ephemeral.distro;

import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.component.DistroCallback;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.component.DistroTransportAgent;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.entity.DistroData;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.entity.DistroKey;
import com.alibaba.nacos.naming.misc.NamingProxy;

import java.util.List;

/**
 * Distro http agent.
 *
 * @author xiweng.yy
 */
public class DistroHttpAgent implements DistroTransportAgent {
    
    @Override
    public boolean syncData(DistroData data, String targetServer) {
        byte[] dataContent = data.getContent();
        return NamingProxy.syncData(dataContent, data.getDistroKey().getTargetServer());
    }
    
    @Override
    public void syncData(DistroData data, String targetServer, DistroCallback callback) {
    
    }
    
    @Override
    public boolean syncVerifyData(DistroData verifyData, String targetServer) {
        NamingProxy.syncCheckSums(verifyData.getContent(), targetServer);
        return true;
    }
    
    @Override
    public void syncVerifyData(DistroData verifyData, String targetServer, DistroCallback callback) {
    
    }
    
    @Override
    public DistroData getData(DistroKey key, String targetServer) {
        return null;
    }
    
    @Override
    public List<DistroData> getDatum(List<DistroKey> keys, String targetServer) {
        return null;
    }
    
    @Override
    public List<DistroData> getAllDatum(String targetServer) {
        return null;
    }
}
