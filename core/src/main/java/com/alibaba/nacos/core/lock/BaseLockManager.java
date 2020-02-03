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

package com.alibaba.nacos.core.lock;

import com.alibaba.nacos.common.model.ResResult;
import com.alibaba.nacos.core.distributed.BizProcessor;
import com.alibaba.nacos.core.distributed.ConsistencyProtocol;
import com.alibaba.nacos.core.distributed.Datum;
import com.alibaba.nacos.core.distributed.LogConsumer;
import com.alibaba.nacos.core.distributed.raft.RaftConfig;
import com.alibaba.nacos.core.distributed.raft.jraft.JRaftProtocol;
import com.alibaba.nacos.core.utils.ResResultUtils;
import com.alibaba.nacos.core.utils.SpringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Component
public abstract class BaseLockManager implements LockManager {

    @Component
    protected static class LockBizProcessor implements BizProcessor {

        private final Map<String, LogConsumer> logConsumerMap = new HashMap<>(8);

        @PostConstruct
        protected void init() {
            final Map<String, BaseLockConsumer> consumerMap = SpringUtils.getBeansOfType(BaseLockConsumer.class);
            final LockManager lockManager = SpringUtils.getBean(LockManager.class);
            consumerMap.forEach((s, baseLockConsumer) ->
            {
                baseLockConsumer.setLockManager(lockManager);
                logConsumerMap.put(baseLockConsumer.operation(), baseLockConsumer);
            });
            ConsistencyProtocol<RaftConfig> protocol = SpringUtils.getBean(JRaftProtocol.class);
            protocol.registerBizProcessor(this);
        }

        @Override
        public void registerLogConsumer(LogConsumer consumer) {

        }

        @Override
        public void deregisterLogConsumer(String operation) {

        }

        @Override
        public <T> T getData(String key) {
            return null;
        }

        @Override
        public ResResult<Boolean> onApply(Datum datum) {
            final String operation = datum.getOperation();
            if (Objects.isNull(LockOperation.sourceOf(operation))) {
                return ResResultUtils.failed("The lock operation is not supported");
            }
            final LogConsumer consumer = logConsumerMap.get(operation);
            try {
                return consumer.onAccept(datum);
            } catch (Exception e) {
                return ResResultUtils.failed(e.getLocalizedMessage());
            }
        }

        @Override
        public Collection<LogConsumer> allLogConsumer() {
            return logConsumerMap.values();
        }

        @Override
        public String bizInfo() {
            return "LOCK";
        }

        @Override
        public boolean interest(String key) {
            return key.contains("LOCK");
        }
    }

}
