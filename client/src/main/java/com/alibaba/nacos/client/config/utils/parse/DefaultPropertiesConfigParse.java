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

package com.alibaba.nacos.client.config.utils.parse;

import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.client.config.utils.AbstractConfigParse;
import com.alibaba.nacos.common.utils.StringUtils;

import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;

/**
 * DefaultPropertiesConfigParse.
 *
 * @author Nacos
 */
public class DefaultPropertiesConfigParse extends AbstractConfigParse {
    
    @Override
    public Properties parse(String configText) {
        Properties properties = new Properties();
        try {
            if (StringUtils.isNotBlank(configText)) {
                properties.load(new StringReader(configText));
            }
        } catch (IOException e) {
            throw new ConfigParseException(e);
        }
        return properties;
    }
    
    @Override
    public String processType() {
        return ConfigType.PROPERTIES.getType();
    }
    
}
