/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.naming.consistency.persistent.impl;

import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.cp.LogProcessor4CP;
import com.alibaba.nacos.consistency.entity.GetRequest;
import com.alibaba.nacos.consistency.entity.Log;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.core.distributed.raft.RaftConfig;
import com.alibaba.nacos.core.storage.RocksStorage;
import com.alibaba.nacos.naming.utils.Constants;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Service
public class PersistentServiceProcessor extends LogProcessor4CP {

    private final CPProtocol<RaftConfig, LogProcessor4CP> protocol;
    private final RocksStorage rocksStorage;
    
    public PersistentServiceProcessor(CPProtocol<RaftConfig, LogProcessor4CP> protocol) {
        this.protocol = protocol;
        this.rocksStorage = new RocksStorage();
    }
    
    private void init() throws Exception {
        this.rocksStorage.init();
        protocol.addLogProcessors(Collections.singletonList(this));
    }
    
    @Override
    public Response onRequest(GetRequest request) {
        return null;
    }
    
    @Override
    public Response onApply(Log log) {
        return null;
    }
    
    @Override
    public String group() {
        return Constants.NAMING_PERSISTENT_SERVICE_GROUP;
    }
    
}
