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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import com.alibaba.nacos.config.server.utils.LogUtil;

import static com.alibaba.nacos.config.server.utils.LogUtil.fatalLog;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Switch
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
        boolean rtn = defaultValue;
        try {
            String value = switches.get(key);
            rtn =  value != null ? Boolean.valueOf(value).booleanValue() : defaultValue;
        } catch (Exception e) {
            rtn = defaultValue;
            LogUtil.fatalLog.error("corrupt switch value {}={}", key, switches.get(key));
        }
        return rtn;
    }

    public static int getSwitchInteger(String key, int defaultValue) {
        int rtn = defaultValue;
        try {
            String status = switches.get(key);
            rtn =  status != null ? Integer.parseInt(status) : defaultValue;
        } catch (Exception e) {
            rtn = defaultValue;
            LogUtil.fatalLog.error("corrupt switch value {}={}", key, switches.get(key));
        }
        return rtn;
    }

    
    public static String getSwitchString(String key, String defaultValue){
    	 String value = switches.get(key);
         return  StringUtils.isBlank(value) ? defaultValue : value ;
    }
    
    public static void load(String config) {
        if (StringUtils.isBlank(config)) {
            fatalLog.error("switch config is blank.");
            return;
        }
        fatalLog.warn("[switch-config] {}", config);

        Map<String, String> map = new HashMap<String, String>(30);
        try {
            for (String line :  IOUtils.readLines(new StringReader(config))) {
                if (!StringUtils.isBlank(line) && !line.startsWith("#")) {
                    String[] array = line.split("=");

                    if (array == null || array.length != 2) {
                        LogUtil.fatalLog.error("corrupt switch record {}", line);
                        continue;
                    }

                    String key = array[0].trim();
                    String value = array[1].trim();

                    map.put(key, value);
                }
                switches = map;
                fatalLog.warn("[reload-switches] {}", getSwitches());
            }
        } catch (IOException e) {
            LogUtil.fatalLog.warn("[reload-switches] error! {}", config);
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
