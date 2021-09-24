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


package com.alibaba.nacos.core.remote.circuitbreaker.rule.tps;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.remote.circuitbreaker.ConfigSerializer;
import com.alibaba.nacos.core.remote.circuitbreaker.pojo.CircuitBreakerPointConfig;
import com.alibaba.nacos.core.remote.circuitbreaker.rule.flow.FlowControlConfig;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.DiskUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import io.jsonwebtoken.io.IOException;
import org.apache.commons.collections.MapUtils;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * @author czf
 */
public class TpsConfigSerializer extends ConfigSerializer {

    @Override
    public String getName() {
        return "default";
    }

    @Override
    public boolean serializeConfig(Map<String, String> configContent) throws IOException {
        try {
            if (configContent.containsKey(POINT_NAME) && configContent.containsKey(POINT_LIST)) {
                String pointName = configContent.get(POINT_NAME);
                List<CircuitBreakerPointConfig<TpsConfig>> configList = JacksonUtils.toObj(configContent.get(POINT_NAME),
                        new TypeReference<List<CircuitBreakerPointConfig<TpsConfig>>>() {});

                // For each config content, serialize its point config and monitorKeyConfig to two separate files.
                for (CircuitBreakerPointConfig<TpsConfig> config : configList) {
                    // Serialize point file.
                    File pointFile = getRuleFile(pointName);
                    if (!pointFile.exists()) {
                        pointFile.createNewFile();
                    }
                    String content = JacksonUtils.toJson(config.getConfig());
                    DiskUtils.writeFile(pointFile, content.getBytes(Constants.ENCODE), false);

                    // Serialize monitor key file separately.
                    if (MapUtils.isNotEmpty(config.getMonitorKeyMap())) {
                        File monitorKeyFile = getMonitorKeyRuleFile(pointName);
                        if (!monitorKeyFile.exists()) {
                            monitorKeyFile.createNewFile();
                        }
                        String monitorKeyContent = JacksonUtils.toJson(config.getMonitorKeyMap());
                        DiskUtils.writeFile(monitorKeyFile, monitorKeyContent.getBytes(Constants.ENCODE), false);
                    }
                }
            }

        } catch (NacosDeserializationException | java.io.IOException ignored) {
            return false;
        }
        return true;
    }

    private File getRuleFile(String pointName) {
        File baseDir = checkBaseDir();
        return new File(baseDir, pointName);
    }

    private File getMonitorKeyRuleFile(String pointName) {
        File baseDir = checkBaseDir();
        return new File(baseDir, pointName + "MonitorKeys");
    }

    private File checkBaseDir() {
        File baseDir = new File(EnvUtil.getNacosHome(), "data" + File.separator + "cb" + File.separator + "tps" + File.separator);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        return baseDir;
    }
}
