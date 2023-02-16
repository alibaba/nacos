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

package com.alibaba.nacos.client.env;

import com.alibaba.nacos.common.utils.ResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Properties;

class DefaultSettingPropertySource extends AbstractPropertySource {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultSettingPropertySource.class);
    
    private static final String DEFAULT_SETTING_PATH = "classpath:nacos_default_setting.properties";
    
    private final Properties defaultSetting = new Properties();
    
    DefaultSettingPropertySource() {
        try (final InputStream inputStream = ResourceUtils.getResourceUrl(DEFAULT_SETTING_PATH).openStream()) {
            defaultSetting.load(inputStream);
        } catch (Exception e) {
            LOGGER.error("load default setting failed", e);
        }
    }
    
    @Override
    SourceType getType() {
        return SourceType.DEFAULT_SETTING;
    }
    
    @Override
    String getProperty(String key) {
        return defaultSetting.getProperty(key);
    }
    
    @Override
    boolean containsKey(String key) {
        return defaultSetting.containsKey(key);
    }
    
    @Override
    Properties asProperties() {
        Properties properties = new Properties();
        properties.putAll(defaultSetting);
        return properties;
    }
}
