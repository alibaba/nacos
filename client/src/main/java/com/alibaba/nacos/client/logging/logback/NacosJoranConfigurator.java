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

package com.alibaba.nacos.client.logging.logback;

import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.event.SaxEvent;

import java.util.List;

/**
 * nacos NacosJoranConfigurator for lobgack.
 * @author hujun
 */
public class NacosJoranConfigurator extends JoranConfigurator {
    @Override
    public void registerSafeConfiguration(List<SaxEvent> eventList) {
        //获取用户保存点
        List<SaxEvent> saxEvents = this.recallSafeConfiguration();
        if (saxEvents != null) {
            super.registerSafeConfiguration(saxEvents);
        }
    }

}
