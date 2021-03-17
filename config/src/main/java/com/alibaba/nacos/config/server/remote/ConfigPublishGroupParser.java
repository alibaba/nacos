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

package com.alibaba.nacos.config.server.remote;

import com.alibaba.nacos.api.config.remote.request.ConfigPublishRequest;
import com.alibaba.nacos.core.remote.control.MonitorKey;
import com.alibaba.nacos.core.remote.control.MonitorKeyParser;

/**
 * parse to get group from config publish parser.
 *
 * @author liuzunfei
 * @version $Id: ConfigPublishGroupParser.java, v 0.1 2021年01月20日 20:38 PM liuzunfei Exp $
 */
public class ConfigPublishGroupParser extends MonitorKeyParser {
    
    /**
     * parse group.
     *
     * @param args parameters.
     * @return
     */
    public MonitorKey parse(Object... args) {
        if (args != null && args.length != 0 && args[0] instanceof ConfigPublishRequest) {
            return new ConfigGroupKey(((ConfigPublishRequest) args[0]).getGroup());
        } else {
            return null;
        }
    }
    
    class ConfigGroupKey extends MonitorKey {
        
        public ConfigGroupKey(String key) {
            this.setKey(key);
        }
        
        @Override
        public String getType() {
            return "group";
        }
    }
}
