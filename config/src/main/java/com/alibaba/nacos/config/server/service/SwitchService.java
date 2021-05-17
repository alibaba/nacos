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

package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.common.utils.IoUtils;
import com.alibaba.nacos.config.server.utils.LogUtil;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import static com.alibaba.nacos.config.server.utils.LogUtil.FATAL_LOG;

/**
 * SwitchService.
 *
 * @author Nacos
 */
@Service
public class SwitchService {
    
    public static final String SWITCH_META_DATAID = "com.alibaba.nacos.meta.switch";
    
    public static final String FIXED_POLLING = "isFixedPolling";
    
    public static final String FIXED_POLLING_INTERVAL = "fixedPollingInertval";
    
    public static final String FIXED_DELAY_TIME = "fixedDelayTime";
    
    public static final String DISABLE_APP_COLLECTOR = "disableAppCollector";
    
    private static volatile Map<String, String> switches = new HashMap<String, String>();
    
    public static boolean getSwitchBoolean(String key, boolean defaultValue) {
        boolean rtn;
        try {
            String value = switches.get(key);
            rtn = value != null ? Boolean.parseBoolean(value) : defaultValue;
        } catch (Exception e) {
            rtn = defaultValue;
            LogUtil.FATAL_LOG.error("corrupt switch value {}={}", key, switches.get(key));
        }
        return rtn;
    }
    
    public static int getSwitchInteger(String key, int defaultValue) {
        int rtn;
        try {
            String status = switches.get(key);
            rtn = status != null ? Integer.parseInt(status) : defaultValue;
        } catch (Exception e) {
            rtn = defaultValue;
            LogUtil.FATAL_LOG.error("corrupt switch value {}={}", key, switches.get(key));
        }
        return rtn;
    }
    
    public static String getSwitchString(String key, String defaultValue) {
        String value = switches.get(key);
        return StringUtils.isBlank(value) ? defaultValue : value;
    }
    
    /**
     * Load config.
     *
     * @param config config content string value.
     */
    public static void load(String config) {
        if (StringUtils.isBlank(config)) {
            FATAL_LOG.error("switch config is blank.");
            return;
        }
        FATAL_LOG.warn("[switch-config] {}", config);
        
        Map<String, String> map = new HashMap<String, String>(30);
        try {
            for (String line : IoUtils.readLines(new StringReader(config))) {
                if (!StringUtils.isBlank(line) && !line.startsWith("#")) {
                    String[] array = line.split("=");
                    
                    if (array == null || array.length != 2) {
                        LogUtil.FATAL_LOG.error("corrupt switch record {}", line);
                        continue;
                    }
                    
                    String key = array[0].trim();
                    String value = array[1].trim();
                    
                    map.put(key, value);
                }
                switches = map;
                FATAL_LOG.warn("[reload-switches] {}", getSwitches());
            }
        } catch (IOException e) {
            LogUtil.FATAL_LOG.warn("[reload-switches] error! {}", config);
        }
    }
    
    public static String getSwitches() {
        StringBuilder sb = new StringBuilder();
        
        String split = "";
        for (Map.Entry<String, String> entry : switches.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            sb.append(split);
            sb.append(key);
            sb.append("=");
            sb.append(value);
            split = "; ";
        }
        
        return sb.toString();
    }
}
