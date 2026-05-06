/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package io.grpc.netty.shaded.io.grpc.netty;

import com.alipay.sofa.jraft.util.internal.ReferenceFieldUpdater;
import com.alipay.sofa.jraft.util.internal.Updaters;
import io.grpc.internal.ServerStream;
import io.grpc.netty.shaded.io.netty.channel.Channel;

/**
 * Get netty channel.
 *
 * @author Weizhan▪Yun
 * @date 2023/1/4 21:08
 */
public class NettyChannelHelper {
    
    private static final ReferenceFieldUpdater<NettyServerStream, WriteQueue> WRITE_QUEUE_GETTER = Updaters.newReferenceFieldUpdater(
            NettyServerStream.class, "writeQueue");
    
    private static final ReferenceFieldUpdater<WriteQueue, Channel> CHANNEL_GETTER = Updaters.newReferenceFieldUpdater(
            WriteQueue.class, "channel");
    
    public static Channel getChannel(final ServerStream stream) {
        if (stream instanceof NettyServerStream) {
            return CHANNEL_GETTER.get(WRITE_QUEUE_GETTER.get((NettyServerStream) stream));
        }
        return null;
    }
}
