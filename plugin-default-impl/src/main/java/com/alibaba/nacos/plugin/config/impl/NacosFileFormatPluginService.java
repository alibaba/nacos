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

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.plugin.config.model.ConfigChangeHandleReport;
import com.alibaba.nacos.plugin.config.spi.AbstractFileFormatPluginService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.json.JacksonJsonParser;
import org.xml.sax.InputSource;
import org.yaml.snakeyaml.Yaml;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NacosFileFormatPluginService.
 *
 * @author liyunfei
 */
public class NacosFileFormatPluginService extends AbstractFileFormatPluginService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NacosFileFormatPluginService.class);
    
    private static final int RPC_ARGS_LENGTH = 2;
    
    /**
     * the relationship of type and function of validating the file.
     */
    private static Map<String, Function<String, Boolean>> fileValidateMap = new HashMap<>();
    
    @Override
    public Object execute(ProceedingJoinPoint pjp, ConfigChangeHandleReport configChangeHandleReport) throws Throwable {
        Object[] args = pjp.getArgs();
        // RPC- dont need to validate
        if (args.length == RPC_ARGS_LENGTH) {
            return pjp.proceed();
        }
        String content = (String) args[5];
        String type = (String) args[13];
        boolean isValidate = validate(content, type);
        if (!isValidate) {
            LOGGER.warn("content of publish content is not consistent with type");
            return RestResultUtils.failed("文件格式内容不一致");
        }
        return pjp.proceed();
    }
    
    @Override
    public String getImplWay() {
        return "nacos";
    }
    
    static {
        loadUtils();
    }
    
    static void loadUtils() {
        fileValidateMap.put("text", textValidate());
        fileValidateMap.put("json", jsonValidate());
        fileValidateMap.put("xml", xmlValidate());
        fileValidateMap.put("html", htmlValidate());
        fileValidateMap.put("properties", propertiesValidate());
        fileValidateMap.put("yaml", yamlValidate());
    }
    
    /**
     * validate file is consistent with type.
     *
     * @param content string content.
     * @param type    file type.
     * @return
     */
    public static boolean validate(String content, String type) {
        Function<String, Boolean> function = null;
        function = fileValidateMap.get(type);
        if (function == null) {
            LOGGER.warn("load {} file format util fail,please add it at {}", type, NacosFileFormatPluginService.class);
            return false;
        }
        return function.apply(content);
    }
    
    /**
     * validate text format.
     *
     * @return
     */
    static Function<String, Boolean> textValidate() {
        return Objects::nonNull;
    }
    
    /**
     * validate json format.
     *
     * @return
     */
    static Function<String, Boolean> jsonValidate() {
        return JSON::isValid;
    }
    
    /**
     * validate xml format.
     *
     * @return
     */
    static Function<String, Boolean> xmlValidate() {
        return (content) -> {
            boolean flag = true;
            try {
                DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
                builder.parse(new InputSource(new StringReader(content)));
            } catch (Exception e) {
                flag = false;
            }
            return flag;
        };
    }
    
    /**
     * validate html format.
     *
     * @return
     */
    static Function<String, Boolean> htmlValidate() {
        String regex = "<([^>]*)>";
        Pattern pattern = Pattern.compile(regex);
        return (content) -> {
            Matcher matcher = pattern.matcher(content);
            return matcher.find();
        };
    }
    
    /**
     * validate properties format.
     *
     * @return
     */
    static Function<String, Boolean> propertiesValidate() {
        return (content) -> {
            try {
                Properties properties = new Properties();
                properties.load(new StringReader(content));
            } catch (Exception e) {
                return false;
            }
            return true;
        };
    }
    
    /**
     * validate yaml format.
     *
     * @return
     */
    static Function<String, Boolean> yamlValidate() {
        return (content) -> {
            Yaml yaml = new Yaml();
            try {
                Object o = yaml.loadAs(content, Object.class);
                if (!(o instanceof LinkedHashMap)) {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
            return true;
        };
    }
}
