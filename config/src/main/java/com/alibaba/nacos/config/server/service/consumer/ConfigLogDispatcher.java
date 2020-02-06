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

package com.alibaba.nacos.config.server.service.consumer;

import com.alibaba.nacos.common.model.ResResult;
import com.alibaba.nacos.core.distributed.ConsistencyProtocol;
import com.alibaba.nacos.core.distributed.Log;
import com.alibaba.nacos.core.distributed.LogConsumer;
import com.alibaba.nacos.core.distributed.LogDispatcher;
import com.alibaba.nacos.core.utils.ResResultUtils;
import com.alibaba.nacos.core.utils.SpringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
@Component
public class ConfigLogDispatcher implements LogDispatcher {

    private ConsistencyProtocol protocol;

    private final Map<String, LogConsumer> consumerMap = new HashMap<>();

    @PostConstruct
    protected void init() {
        protocol = SpringUtils.getBean("RaftProtocol", ConsistencyProtocol.class);
        protocol.registerBizProcessor(this);
        Map<String, ConfigConsumer> beans = SpringUtils.getBeansOfType(ConfigConsumer.class);
        for (ConfigConsumer consumer : beans.values()) {
            registerLogConsumer(consumer);
        }
    }

    @Override
    public synchronized void registerLogConsumer(LogConsumer consumer) {
        consumerMap.put(consumer.operation(), consumer);
    }

    @Override
    public synchronized void deregisterLogConsumer(String operation) {
        consumerMap.remove(operation);
    }

    @Override
    public <T> T getData(String key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResResult<Boolean> onApply(Log log) {
        final String operation = log.getOperation();
        final LogConsumer consumer = consumerMap.get(operation);
        if (Objects.nonNull(consumer)) {
            return consumer.onAccept(log);
        }
        return ResResultUtils.failed("The operation is not supported");
    }

    @Override
    public Collection<LogConsumer> allLogConsumer() {
        return consumerMap.values();
    }

    @Override
    public String bizInfo() {
        return "CONFIG";
    }

    @Override
    public boolean interest(String key) {
        return StringUtils.contains(key, bizInfo());
    }
}
