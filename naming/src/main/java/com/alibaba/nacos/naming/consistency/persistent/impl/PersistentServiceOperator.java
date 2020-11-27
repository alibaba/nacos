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
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.entity.Response;

/**
 * To handle v1 and V2's handling of persistent services.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public abstract class PersistentServiceOperator {
    
    protected final Serializer serializer = SerializeFactory.getDefault();
    
    private PersistentServiceProcessor processor;
    
    protected final void setProcessor(PersistentServiceProcessor processor) {
        this.processor = processor;
    }
    
    protected Response read(final Object ctx) throws Exception {
        return processor.read(ctx);
    }
    
    protected Response write(final BatchWriteRequest request, final String op) throws Exception {
        return write(StringUtils.EMPTY, request, op);
    }
    
    protected Response write(final String key, final BatchWriteRequest request, final String op) throws Exception {
        return processor.write(this, key, op, request);
    }
    
    protected abstract void onApply(Op op, BatchWriteRequest request);
    
    protected final boolean interested(final String key) {
        return key.startsWith(prefix());
    }
    
    // TODO So we have to think about whether or not
    protected abstract String prefix();
    
}
