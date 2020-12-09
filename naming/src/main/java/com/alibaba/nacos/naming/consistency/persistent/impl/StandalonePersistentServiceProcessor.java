/*
 *  Copyright 1999-2018 Alibaba Group Holding Ltd.
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
 */

package com.alibaba.nacos.naming.consistency.persistent.impl;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.core.code.ConditionOnStandaloneMode;
import com.alibaba.nacos.naming.utils.Constants;
import com.google.protobuf.ByteString;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Persistent service manipulation layer in stand-alone mode.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Conditional(value = ConditionOnStandaloneMode.class)
@Component
public class StandalonePersistentServiceProcessor extends BasePersistentServiceProcessor {
    
    public StandalonePersistentServiceProcessor() throws Exception {
    }
    
    @Override
    public boolean isAvailable() {
        return true;
    }
    
    @Override
    protected Response read(Object ctx) {
        final ReadRequest req = ReadRequest.newBuilder().setGroup(Constants.NAMING_PERSISTENT_SERVICE_GROUP)
                .setData(ByteString.copyFrom(serializer.serialize(ctx))).build();
        return onRequest(req);
    }
    
    @Override
    protected Response write(BatchWriteRequest request, String op) {
        return write(StringUtils.EMPTY, op, request);
    }
    
    @Override
    protected Response write(String key, String op, BatchWriteRequest request) {
        final WriteRequest req = WriteRequest.newBuilder().setKey(key)
                .setData(ByteString.copyFrom(serializer.serialize(request))).setGroup(group()).setOperation(op).build();
        return onApply(req);
    }
    
    @Override
    protected Response write(String key, String op, BatchWriteRequest request, Map<String, String> extendInfo)
            throws Exception {
        final WriteRequest req = WriteRequest.newBuilder().setKey(key)
                .setData(ByteString.copyFrom(serializer.serialize(request))).setGroup(group())
                .putAllExtendInfo(extendInfo).setOperation(op).build();
        return onApply(req);
    }
    
}
