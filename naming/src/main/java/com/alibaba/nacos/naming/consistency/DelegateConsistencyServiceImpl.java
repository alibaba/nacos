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
import com.alibaba.nacos.naming.consistency.persistent.ClusterVersionJudgement;
import com.alibaba.nacos.naming.consistency.persistent.PersistentConsistencyService;
import com.alibaba.nacos.naming.consistency.persistent.impl.PersistentServiceProcessor;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftConsistencyServiceImpl;
import com.alibaba.nacos.naming.pojo.Record;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

/**
 * Consistency delegate.
 *
 * @author nkorange
 * @since 1.0.0
 */
@DependsOn("ProtocolManager")
@Service("consistencyDelegate")
public class DelegateConsistencyServiceImpl implements ConsistencyService {
    
    private final ClusterVersionJudgement versionJudgement;
    
    private final RaftConsistencyServiceImpl oldPersistentConsistencyService;
    
    private final PersistentServiceProcessor newPersistentConsistencyService;
    
    private final EphemeralConsistencyService ephemeralConsistencyService;
    
    private volatile boolean switchNewPersistentService = false;
    
    public DelegateConsistencyServiceImpl(ClusterVersionJudgement versionJudgement,
            RaftConsistencyServiceImpl oldPersistentConsistencyService,
            PersistentServiceProcessor newPersistentConsistencyService,
            EphemeralConsistencyService ephemeralConsistencyService) {
        this.versionJudgement = versionJudgement;
        this.oldPersistentConsistencyService = oldPersistentConsistencyService;
        this.newPersistentConsistencyService = newPersistentConsistencyService;
        this.ephemeralConsistencyService = ephemeralConsistencyService;
        this.init();
    }
    
    private void init() {
        this.versionJudgement.registerObserver(isAllNewVersion -> switchNewPersistentService = isAllNewVersion);
    }
    
    @Override
    public void put(String key, Record value) throws NacosException {
        mapConsistencyService(key).put(key, value);
    }
    
    @Override
    public void remove(String key) throws NacosException {
        mapConsistencyService(key).remove(key);
    }
    
    @Override
    public Datum get(String key) throws NacosException {
        return mapConsistencyService(key).get(key);
    }
    
    @Override
    public void listen(String key, RecordListener listener) throws NacosException {
        
        // this special key is listened by both:
        if (KeyBuilder.SERVICE_META_KEY_PREFIX.equals(key)) {
            oldPersistentConsistencyService.listen(key, listener);
            newPersistentConsistencyService.listen(key, listener);
            ephemeralConsistencyService.listen(key, listener);
            return;
        }
        
        mapConsistencyService(key).listen(key, listener);
    }
    
    @Override
    public void unListen(String key, RecordListener listener) throws NacosException {
        ConsistencyService service = mapConsistencyService(key);
        service.unListen(key, listener);
        if (service instanceof PersistentConsistencyService && !switchNewPersistentService) {
            newPersistentConsistencyService.unListen(key, listener);
        }
    }
    
    @Override
    public boolean isAvailable() {
        return ephemeralConsistencyService.isAvailable() && switchOne().isAvailable();
    }
    
    private ConsistencyService mapConsistencyService(String key) {
        return KeyBuilder.matchEphemeralKey(key) ? ephemeralConsistencyService : switchOne();
    }
    
    private PersistentConsistencyService switchOne() {
        return switchNewPersistentService ? newPersistentConsistencyService : oldPersistentConsistencyService;
    }
}
