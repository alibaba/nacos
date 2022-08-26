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
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.RecordListener;
import com.alibaba.nacos.naming.consistency.persistent.impl.BasePersistentServiceProcessor;
import com.alibaba.nacos.naming.consistency.persistent.impl.PersistentServiceProcessor;
import com.alibaba.nacos.naming.consistency.persistent.impl.StandalonePersistentServiceProcessor;
import com.alibaba.nacos.naming.pojo.Record;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Persistent consistency service delegate.
 *
 * @author xiweng.yy
 */
@Component("persistentConsistencyServiceDelegate")
public class PersistentConsistencyServiceDelegateImpl implements PersistentConsistencyService {
    
    private final BasePersistentServiceProcessor newPersistentConsistencyService;
    
    public PersistentConsistencyServiceDelegateImpl(ProtocolManager protocolManager) throws Exception {
        this.newPersistentConsistencyService = createNewPersistentServiceProcessor(protocolManager);
    }
    
    @Override
    public void put(String key, Record value) throws NacosException {
        switchOne().put(key, value);
    }
    
    @Override
    public void remove(String key) throws NacosException {
        switchOne().remove(key);
    }
    
    @Override
    public Datum get(String key) throws NacosException {
        return switchOne().get(key);
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
        return switchOne().isAvailable();
    }
    
    @Override
    public Optional<String> getErrorMsg() {
        return switchOne().getErrorMsg();
    }
    
    private PersistentConsistencyService switchOne() {
        return newPersistentConsistencyService;
    }
    
    private BasePersistentServiceProcessor createNewPersistentServiceProcessor(ProtocolManager protocolManager)
            throws Exception {
        final BasePersistentServiceProcessor processor =
                EnvUtil.getStandaloneMode() ? new StandalonePersistentServiceProcessor()
                        : new PersistentServiceProcessor(protocolManager);
        processor.afterConstruct();
        return processor;
    }
}
