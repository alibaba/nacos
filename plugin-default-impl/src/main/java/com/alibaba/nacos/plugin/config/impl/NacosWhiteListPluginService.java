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

package com.alibaba.nacos.plugin.config.impl;

import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.config.server.utils.ZipUtils;
import com.alibaba.nacos.plugin.config.model.ConfigChangeRequest;
import com.alibaba.nacos.plugin.config.model.ConfigChangeResponse;
import com.alibaba.nacos.plugin.config.spi.AbstractWhiteListPluginService;
import com.alibaba.nacos.plugin.config.util.ConfigPropertyUtil;
import io.grpc.netty.shaded.io.netty.handler.codec.string.LineSeparator;
import org.apache.http.entity.ContentType;
import org.aspectj.lang.ProceedingJoinPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * NacosWhiteListPluginService.
 *
 * @author liyunfei
 */
public class NacosWhiteListPluginService extends AbstractWhiteListPluginService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NacosWhiteListPluginService.class);
    
    @Override
    public void execute(ProceedingJoinPoint pjp, ConfigChangeRequest configChangeRequest,
            ConfigChangeResponse configChangeResponse) {
        String whiteListUrls = ConfigPropertyUtil.getWhiteListUrls();
        String[] whiteLists = whiteListUrls.split("\\,");
        // is convenient to contains judge
        Set<String> whiteList = Arrays.stream(whiteLists).collect(Collectors.toSet());
        Object[] args = pjp.getArgs();
        try {
            filterFile(args, whiteList);
            configChangeResponse.setRetVal(pjp.proceed(args));
        } catch (Throwable e) {
            LOGGER.warn("filter import file by whitelist failed {}", e.getMessage());
            configChangeResponse.setRetVal(RestResultUtils.failed(e.getMessage()));
        }
    }
    
    void filterFile(Object[] args, Set<String> whiteList) throws IOException {
        // find the file and filter it
        for (int index = 0; index < args.length; index++) {
            if (args[index] instanceof MultipartFile) {
                MultipartFile file = (MultipartFile) args[index];
                ZipUtils.UnZipResult unziped = ZipUtils.unzip(file.getBytes());
                if (unziped.getMetaDataItem() == null) {
                    LOGGER.warn("whitelist plugin service can not support V2 export file");
                    return;
                }
                String metaData = unziped.getMetaDataItem().getItemData();
                Map<String, Object> map = parseYamlString(metaData);
                ArrayList<LinkedHashMap<String, String>> lists;
                
                try {
                    lists = (ArrayList<LinkedHashMap<String, String>>) map.get("metadata");
                } catch (ClassCastException e) {
                    LOGGER.error("load import file meta data fail,can not execute the whitelist plugin service");
                    return;
                }
                
                Map<String, String> dataIdTypeMap = new HashMap<>(8);
                List<ZipUtils.ZipItem> itemList = new ArrayList<>();
                lists.forEach(item0 -> {
                    dataIdTypeMap.put(item0.get("group") + "/" + item0.get("dataId"), item0.get("type"));
                });
                unziped.getZipItemList().forEach(u -> {
                    String itemName = u.getItemName();
                    String fileType = dataIdTypeMap.get(itemName);
                    if (whiteList.contains(fileType)) {
                        itemList.add(u);
                    }
                });
                byte[] fileByFilteredBytes = ZipUtils.zip(itemList);
                MultipartFile fileByFiltered = new MockMultipartFile(ContentType.APPLICATION_OCTET_STREAM.toString(),
                        fileByFilteredBytes);
                args[index] = fileByFiltered;
            }
        }
    }
    
    /**
     * parse yaml string.
     *
     * @param yaml parse yaml to map
     * @return map
     */
    public static Map<String, Object> parseYamlString(String yaml) {
        yaml = yaml.replace("\\r", "");
        yaml = yaml.replace("\\n", LineSeparator.DEFAULT.toString());
        LinkedHashMap<String, Object> sourceMap = null;
        try {
            sourceMap = new Yaml().loadAs(yaml, LinkedHashMap.class);
        } catch (Exception e) {
            LOGGER.error("parseYamlString fail", e);
        }
        
        if (Objects.isNull(sourceMap)) {
            return Collections.EMPTY_MAP;
        }
        LinkedHashMap<String, Object> targetMap = new LinkedHashMap<>(sourceMap.size());
        fillAllPathMap(sourceMap, targetMap, "");
        return targetMap;
    }
    
    private static void fillAllPathMap(Map<String, Object> sourceMap, Map<String, Object> targetMap, String prefix) {
        prefix = StringUtils.isEmpty(prefix) ? prefix : prefix + ".";
        for (Map.Entry<String, Object> entry : sourceMap.entrySet()) {
            Object value = entry.getValue();
            if (Objects.nonNull(value) && (value instanceof Map)) {
                fillAllPathMap((Map) value, targetMap, prefix + entry.getKey());
            } else {
                targetMap.put(prefix + entry.getKey(), value);
            }
        }
    }
}
