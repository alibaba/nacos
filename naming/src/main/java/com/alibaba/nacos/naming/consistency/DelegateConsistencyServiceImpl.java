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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Publish execution delegate
 *
 * @author nkorange
 * @since 1.0.0
 */
@Component("consistencyDelegate")
public class DelegateConsistencyServiceImpl implements ConsistencyService {

    @Autowired
    private PersistentConsistencyService persistentConsistencyService;

    @Autowired
    private EphemeralConsistencyService ephemeralConsistencyService;

    @Override
    public void put(Object key, Object value) throws NacosException {
        persistentConsistencyService.put(key, value);
    }

    @Override
    public void remove(Object key) throws NacosException {
        persistentConsistencyService.remove(key);
    }

    @Override
    public Object get(Object key) throws NacosException {
        return persistentConsistencyService.get(key);
    }

    @Override
    public void listen(Object key, DataListener listener) throws NacosException {
        persistentConsistencyService.listen(key, listener);
    }

    @Override
    public void unlisten(Object key, DataListener listener) throws NacosException {
        persistentConsistencyService.unlisten(key, listener);
    }

    @Override
    public boolean isResponsible(Object key) {
        return true;
    }

    @Override
    public String getResponsibleServer(Object key) {
        return null;
    }
}
