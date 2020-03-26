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

package com.alibaba.nacos.core.distributed.distro;

import com.alibaba.nacos.consistency.LogFuture;
import com.alibaba.nacos.consistency.LogProcessor;
import com.alibaba.nacos.consistency.ap.APProtocol;
import com.alibaba.nacos.consistency.ap.LogProcessor4AP;
import com.alibaba.nacos.consistency.entity.GetRequest;
import com.alibaba.nacos.consistency.entity.GetResponse;
import com.alibaba.nacos.consistency.entity.Log;
import com.alibaba.nacos.consistency.exception.NoSuchLogProcessorException;
import com.alibaba.nacos.core.cluster.MemberManager;
import com.alibaba.nacos.core.distributed.AbstractConsistencyProtocol;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * // TODO To be implemented
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class DistroProtocol extends AbstractConsistencyProtocol<DistroConfig, LogProcessor4AP> implements APProtocol<DistroConfig> {

    private final AtomicBoolean initialize = new AtomicBoolean(false);
    private MemberManager memberManager;
    private DistroConfig distroConfig;

    public DistroProtocol(MemberManager memberManager) {
        this.memberManager = memberManager;
    }

    @Override
    public void init(DistroConfig config) {
        if (initialize.compareAndSet(false, true)) {
            this.distroConfig = config;
        }
    }

    @Override
    public GetResponse getData(GetRequest request) throws Exception {
        final String group = request.getGroup();
        LogProcessor processor = allProcessor().get(group);
        if (processor != null) {
            return processor.getData(request);
        }
        throw new NoSuchLogProcessorException(group);
    }

    @Override
    public LogFuture submit(Log data) throws Exception {
        return LogFuture.success(null);
    }

    @Override
    public CompletableFuture<LogFuture> submitAsync(Log data) {
        return CompletableFuture.completedFuture(LogFuture.success(null));
    }

    @Override
    public void addMembers(Set<String> addresses) {
        distroConfig.addMembers(addresses);
    }

    @Override
    public void removeMembers(Set<String> addresses) {
        distroConfig.removeMembers(addresses);
    }

    @Override
    public void shutdown() {

    }

}
