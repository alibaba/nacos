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
package com.alibaba.nacos.naming.consistency;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.naming.consistency.ephemeral.EphemeralConsistencyService;
import com.alibaba.nacos.naming.consistency.persistent.PersistentConsistencyService;
import com.alibaba.nacos.naming.core.DistroMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Publish execution delegate
 *
 * @author nkorange
 * @since 1.0.0
 */
@Service("consistencyDelegate")
public class DelegateConsistencyServiceImpl implements ConsistencyService {

    @Autowired
    private PersistentConsistencyService persistentConsistencyService;

    @Autowired
    private DistroMapper distroMapper;

    @Autowired
    private EphemeralConsistencyService ephemeralConsistencyService;

    @Override
    public void put(String key, Object value) throws NacosException {
        if (KeyBuilder.matchEphemeralKey(key)) {
            ephemeralConsistencyService.put(key, value);
        } else {
            persistentConsistencyService.put(key, value);
        }
    }

    @Override
    public void remove(String key) throws NacosException {
        if (KeyBuilder.matchEphemeralKey(key)) {
            ephemeralConsistencyService.remove(key);
        } else {
            persistentConsistencyService.remove(key);
        }
    }

    @Override
    public Datum get(String key) throws NacosException {
        if (KeyBuilder.matchEphemeralKey(key)) {
            return ephemeralConsistencyService.get(key);
        } else {
            return persistentConsistencyService.get(key);
        }
    }

    @Override
    public void listen(String key, DataListener listener) throws NacosException {
        if (KeyBuilder.matchEphemeralKey(key)) {
            ephemeralConsistencyService.listen(key, listener);
        } else {
            persistentConsistencyService.listen(key, listener);
        }
    }

    @Override
    public void unlisten(String key, DataListener listener) throws NacosException {
        if (KeyBuilder.matchEphemeralKey(key)) {
            ephemeralConsistencyService.unlisten(key, listener);
        } else {
            persistentConsistencyService.unlisten(key, listener);
        }
    }

    @Override
    public boolean isResponsible(String key) {
        return distroMapper.responsible(KeyBuilder.getServiceName(key));
    }

    @Override
    public String getResponsibleServer(String key) {
        return distroMapper.mapSrv(KeyBuilder.getServiceName(key));
    }

    @Override
    public boolean isAvailable() {
        return ephemeralConsistencyService.isAvailable() && persistentConsistencyService.isAvailable();
    }
}
