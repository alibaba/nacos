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

package com.alibaba.nacos.config.server.aspect;

import com.alibaba.nacos.config.server.exception.CircuitException;
import com.alibaba.nacos.config.server.model.event.RaftDBErrorEvent;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.core.notify.Event;
import com.alibaba.nacos.core.notify.NotifyCenter;
import com.alibaba.nacos.core.notify.listener.Subscribe;
import com.alibaba.nacos.core.utils.SpringUtils;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.PostConstruct;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Aspect
@Component
public class CircuitAspect {

    private final AtomicBoolean openFusing = new AtomicBoolean(false);

    @PostConstruct
    protected void init() {

        openFusing.set(SpringUtils.getProperty("nacos.config.open-circuit", Boolean.class, false));

        NotifyCenter.registerSubscribe(new Subscribe<RaftDBErrorEvent>() {
            @Override
            public void onEvent(RaftDBErrorEvent event) {

                // If distributed storage is enabled internally, circuit breakers are forced to be turned on

                openFusing.set(openFusing.get() || PropertyUtil.isEmbeddedDistributedStorage());
            }

            @Override
            public Class<? extends Event> subscribeType() {
                return RaftDBErrorEvent.class;
            }
        });
    }

    @Pointcut("@annotation(com.alibaba.nacos.config.server.annonation.Circuit)")
    private void circuit() {
    }

    // TODO More circuit breakers can be added

    @Around(value = "circuit()")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {

        if (openFusing.get()) {
            throw new CircuitException();
        }

        return pjp.proceed();
    }

}
