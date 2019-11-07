/*
 * Copyright 1999-2019 Alibaba Group Holding Ltd.
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
package com.alibaba.nacos.naming.consistency.weak.tree;

import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.weak.tree.remoting.ServerUserProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * @author satjd
 */
@Component
public class TransferService {
    @Autowired
    TreePeerSet treePeerSet;

    @Autowired
    TransferTaskBatchProcessor transferTaskBatchProcessor;

    @Autowired
    TransferTaskSingleProcessor transferTaskSingleProcessor;

    @Autowired
    ProtocolConfig protocolConfig;

    @Autowired
    ServerUserProcessor serverUserProcessor;

    @PostConstruct
    void init() {
    }

    public void transferNext(Datum datum, DatumType type) throws Exception {
        transferNext(datum, type, treePeerSet.getLocal());
    }

    public void transferNext(Datum datum, DatumType type, TreePeer source) throws Exception {
        if (protocolConfig.isBatchUpdateEnabled() && type.equals(DatumType.UPDATE)) {
            // 采用聚合更新
            transferBatch(datum, type, source);
            return;
        }

        transferSingle(datum, type, source);
    }

    private void transferSingle(Datum datum, DatumType type, TreePeer source) {
        transferTaskSingleProcessor.addTask(new TransferTask(datum,type,source));
    }
    private void transferBatch(Datum datum, DatumType type, TreePeer source) {
        transferTaskBatchProcessor.addTask(new TransferTask(datum,type,source));
    }
}
