/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.remote.grpc;

import java.util.concurrent.atomic.AtomicLong;

/**
 * id generator to server push ack.
 *
 * @author liuzunfei
 * @version $Id: PushAckIdGenerator.java, v 0.1 2020年07月20日 5:49 PM liuzunfei Exp $
 */
public class PushAckIdGenerator {
    
    private static AtomicLong id = new AtomicLong(0L);
    
    private static final int ID_PREV_REGEN_OFFSET = 1000;
    
    /**
     * get server push id.
     */
    public static long getNextId() {
        if (id.longValue() > Long.MAX_VALUE - ID_PREV_REGEN_OFFSET) {
            id.getAndSet(0L);
        }
        return id.incrementAndGet();
    }
    
}
