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

package com.alibaba.nacos.core.utils;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * @author Nacos
 */
public class PropertyUtil {
    private static Properties properties = new Properties();
    private static final Logger log = LoggerFactory.getLogger(PropertyUtil.class);

    static {
        InputStream inputStream = null;
        try {
            String baseDir = System.getProperty("nacos.home");
            if (!StringUtils.isBlank(baseDir)) {
                inputStream = new FileInputStream(baseDir + "/conf/application.properties");
            } else {
                inputStream = PropertyUtil.class
                    .getResourceAsStream("/application.properties");
            }
            properties.load(inputStream);
        } catch (Exception e) {
            log.error("read property file error:" + e);
        } finally {
            IOUtils.closeQuietly(inputStream);
        }
    }

    public static String getProperty(String key) {
        return properties.getProperty(key);
    }

    public static String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public static List<String> getPropertyList(String key) {
        List<String> valueList = new ArrayList<>();

        for (int i = 0; i < Integer.MAX_VALUE; i++) {
            String value = properties.getProperty(key + "[" + i + "]");
            if (StringUtils.isBlank(value)) {
                break;
            }

            valueList.add(value);
        }

        return valueList;
    }

}
