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

package com.alibaba.nacos.naming.consistency.persistent.impl;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.cp.MetadataKey;
import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.core.code.ConditionOnClusterMode;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.utils.Constants;
import com.google.protobuf.ByteString;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * In cluster mode, start the Raft protocol.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("PMD.ServiceOrDaoClassShouldEndWithImplRule")
@Conditional(value = ConditionOnClusterMode.class)
@Component
public class ClusterPersistentServiceProcessor extends BasePersistentServiceProcessor {
    
    private final CPProtocol protocol;
    
    private final Serializer serializer;
    
    /**
     * Is there a leader node currently.
     */
    private volatile boolean hasLeader = false;
    
    /**
     * Whether an unrecoverable error occurred.
     */
    private volatile boolean hasError = false;
    
    public ClusterPersistentServiceProcessor() throws Exception {
        this.protocol = ProtocolManager.getCpProtocol();
        this.serializer = SerializeFactory.getSerializer("JSON");
        init();
    }
    
    @Override
    public boolean isAvailable() {
        return hasLeader && !hasError;
    }
    
    @SuppressWarnings("unchecked")
    private void init() {
        this.protocol.addRequestProcessors(Collections.singletonList(this));
        this.protocol.protocolMetaData()
                .subscribe(Constants.NAMING_PERSISTENT_SERVICE_GROUP, MetadataKey.LEADER_META_DATA,
                        (o, arg) -> hasLeader = StringUtils.isNotBlank(String.valueOf(arg)));
        // If you choose to use the new RAFT protocol directly, there will be no compatible logical execution
        waitLeader();
    }
    
    private void waitLeader() {
        while (!hasLeader && !hasError) {
            Loggers.RAFT.info("Waiting Jraft leader vote ...");
            try {
                TimeUnit.MILLISECONDS.sleep(500);
            } catch (InterruptedException ignored) {
            }
        }
    }
    
    @Override
    protected Response read(Object ctx) throws Exception {
        final ReadRequest req = ReadRequest.newBuilder().setGroup(Constants.NAMING_PERSISTENT_SERVICE_GROUP)
                .setData(ByteString.copyFrom(serializer.serialize(ctx))).build();
        return protocol.getData(req);
    }
    
    @Override
    protected Response write(final BatchWriteRequest request, final String op) throws Exception {
        return write(StringUtils.EMPTY, op, request);
    }
    
    @Override
    protected Response write(final String key, final String op, final BatchWriteRequest request) throws Exception {
        return protocol
                .write(WriteRequest.newBuilder().setKey(key).setData(ByteString.copyFrom(serializer.serialize(request)))
                        .setGroup(group()).setOperation(op).build());
    }
    
    @Override
    protected Response write(String key, String op, BatchWriteRequest request, Map<String, String> extendInfo)
            throws Exception {
        return protocol
                .write(WriteRequest.newBuilder().setKey(key).setData(ByteString.copyFrom(serializer.serialize(request)))
                        .setGroup(group()).setOperation(op).putAllExtendInfo(extendInfo).build());
    }
}
