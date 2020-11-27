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

package com.alibaba.nacos.naming.consistency.persistent;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.RecordListener;
import com.alibaba.nacos.naming.core.PersistentServiceV1Operator;
import com.alibaba.nacos.naming.pojo.Record;
import org.springframework.stereotype.Component;

/**
 * Persistent consistency service delegate.
 *
 * @author xiweng.yy
 */
@Component("persistentConsistencyServiceDelegate")
public class PersistentConsistencyServiceDelegateImpl implements PersistentConsistencyService {
    
    private final PersistentServiceV1Operator newPersistentConsistencyService;
    
    public PersistentConsistencyServiceDelegateImpl(PersistentServiceV1Operator newPersistentConsistencyService) {
        this.newPersistentConsistencyService = newPersistentConsistencyService;
    }
    
    @Override
    public void put(String key, Record value) throws NacosException {
        newPersistentConsistencyService.put(key, value);
    }
    
    @Override
    public void remove(String key) throws NacosException {
        newPersistentConsistencyService.remove(key);
    }
    
    @Override
    public Datum get(String key) throws NacosException {
        return newPersistentConsistencyService.get(key);
    }
    
    @Override
    public void listen(String key, RecordListener listener) throws NacosException {
        newPersistentConsistencyService.listen(key, listener);
    }
    
    @Override
    public void unListen(String key, RecordListener listener) throws NacosException {
        newPersistentConsistencyService.unListen(key, listener);
    }
    
    @Override
    public boolean isAvailable() {
        return newPersistentConsistencyService.isAvailable();
    }
    
}
