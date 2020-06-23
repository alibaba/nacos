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

package com.alibaba.nacos.common.notify;

import com.alibaba.nacos.common.notify.listener.SmartSubscriber;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.ClassUtils;
import com.alibaba.nacos.common.utils.Objects;

/**
 * The default share event publisher implementation for slow event.
 *
 * @author zongtanghu
 */
public class DefaultSharePublisher extends DefaultPublisher {
    
    @Override
    public void receiveEvent(Event event) {
        final long currentEventSequence = event.sequence();
        final String sourceName = ClassUtils.getName(event);
        
        // Notification single event listener
        for (Subscriber subscriber : subscribers) {
            // Whether to ignore expiration events
            if (subscriber.ignoreExpireEvent() && lastEventSequence > currentEventSequence) {
                LOGGER.debug("[NotifyCenter] the {} is unacceptable to this subscriber, because had expire",
                        event.getClass());
                continue;
            }
            
            if (subscriber instanceof SmartSubscriber) {
                // For SmartSubscriber instance.
                
                SmartSubscriber smartSubscriber = (SmartSubscriber) subscriber;
                
                for (Class<? extends Event> subType : smartSubscriber.subscribeTypes()) {
                    // Judge whether smartSubscriber has subscribed this type of event.
                    if (ClassUtils.getName(subType).equals(sourceName)) {
                        notifySubscriber(subscriber, event);
                        break;
                    }
                }
            } else {
                // For subscriber instance.
                
                final String targetName = ClassUtils.getName(subscriber.subscribeType());
                if (!Objects.equals(sourceName, targetName)) {
                    continue;
                }
                notifySubscriber(subscriber, event);
                continue;
            }
        }
    }
}
