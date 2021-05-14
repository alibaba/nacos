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

package com.alibaba.nacos.naming.consistency.ephemeral.distro.component;

import com.alibaba.nacos.consistency.DataOperation;
import com.alibaba.nacos.core.distributed.distro.component.DistroDataStorage;
import com.alibaba.nacos.core.distributed.distro.entity.DistroData;
import com.alibaba.nacos.core.distributed.distro.entity.DistroKey;
import com.alibaba.nacos.naming.cluster.transport.Serializer;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.DataStore;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.combined.DistroHttpCombinedKey;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.sys.utils.ApplicationUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Distro data storage impl.
 *
 * @author xiweng.yy
 */
public class DistroDataStorageImpl implements DistroDataStorage {
    
    private final DataStore dataStore;
    
    private final DistroMapper distroMapper;
    
    private volatile boolean isFinishInitial;
    
    public DistroDataStorageImpl(DataStore dataStore, DistroMapper distroMapper) {
        this.dataStore = dataStore;
        this.distroMapper = distroMapper;
    }
    
    @Override
    public void finishInitial() {
        isFinishInitial = true;
    }
    
    @Override
    public boolean isFinishInitial() {
        return isFinishInitial;
    }
    
    @Override
    public DistroData getDistroData(DistroKey distroKey) {
        Map<String, Datum> result = new HashMap<>(1);
        if (distroKey instanceof DistroHttpCombinedKey) {
            result = dataStore.batchGet(((DistroHttpCombinedKey) distroKey).getActualResourceTypes());
        } else {
            Datum datum = dataStore.get(distroKey.getResourceKey());
            result.put(distroKey.getResourceKey(), datum);
        }
        byte[] dataContent = ApplicationUtils.getBean(Serializer.class).serialize(result);
        return new DistroData(distroKey, dataContent);
    }
    
    @Override
    public DistroData getDatumSnapshot() {
        Map<String, Datum> result = dataStore.getDataMap();
        byte[] dataContent = ApplicationUtils.getBean(Serializer.class).serialize(result);
        DistroKey distroKey = new DistroKey("snapshot", KeyBuilder.INSTANCE_LIST_KEY_PREFIX);
        return new DistroData(distroKey, dataContent);
    }
    
    @Override
    public List<DistroData> getVerifyData() {
        Map<String, String> keyChecksums = new HashMap<>(64);
        for (String key : dataStore.keys()) {
            if (!distroMapper.responsible(KeyBuilder.getServiceName(key))) {
                continue;
            }
            Datum datum = dataStore.get(key);
            if (datum == null) {
                continue;
            }
            keyChecksums.put(key, datum.value.getChecksum());
        }
        if (keyChecksums.isEmpty()) {
            return Collections.emptyList();
        }
        DistroKey distroKey = new DistroKey("checksum", KeyBuilder.INSTANCE_LIST_KEY_PREFIX);
        DistroData data = new DistroData(distroKey, ApplicationUtils.getBean(Serializer.class).serialize(keyChecksums));
        data.setType(DataOperation.VERIFY);
        return Collections.singletonList(data);
    }
}
