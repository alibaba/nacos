/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.copilot.config;

import com.alibaba.nacos.common.utils.JacksonUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One read of the stored Copilot configuration document.
 *
 * <p>The parsed values and the raw JSON fields are kept together, so a field the document
 * does not contain can be told apart from a field it explicitly clears.
 *
 * @author nacos
 */
public final class StoredCopilotConfig {
    
    public static final String ENABLED = "enabled";
    
    public static final String DEFAULT_NAMESPACE = "defaultNamespace";
    
    public static final String API_KEY = "apiKey";
    
    public static final String API_KEY_CLEARED = "apiKeyCleared";
    
    public static final String MODEL = "model";
    
    public static final String STUDIO_URL = "studioUrl";
    
    public static final String STUDIO_PROJECT = "studioProject";
    
    public static final String SELECTED_CONNECTION = "selectedConnection";
    
    private final String content;
    
    private final Map<String, Object> fields;
    
    private final CopilotProperties properties;
    
    private StoredCopilotConfig(String content, Map<String, Object> fields,
        CopilotProperties properties) {
        this.content = content;
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        this.properties = properties;
    }
    
    /**
     * Parse one stored document.
     *
     * @param content document content, never blank
     * @return parsed document
     */
    @SuppressWarnings("unchecked")
    public static StoredCopilotConfig parse(String content) {
        Map<String, Object> fields = JacksonUtils.toObj(content, Map.class);
        CopilotProperties properties = JacksonUtils.toObj(content, CopilotProperties.class);
        return new StoredCopilotConfig(content,
            fields == null ? new LinkedHashMap<>() : fields,
            properties == null ? new CopilotProperties() : properties);
    }
    
    /**
     * Raw content of the document, used as the compare-and-set base of the next save.
     *
     * @return content
     */
    public String getContent() {
        return content;
    }
    
    /**
     * Whether the document declares the given field, even when its value is blank.
     *
     * @param field field name
     * @return {@code true} when the field is present
     */
    public boolean declares(String field) {
        return fields.containsKey(field);
    }
    
    /**
     * Raw value of a declared field.
     *
     * @param field field name
     * @return raw value, or {@code null} when the field is absent or declared as null
     */
    public Object getRaw(String field) {
        return fields.get(field);
    }
    
    /**
     * Field values parsed from the same read.
     *
     * @return parsed configuration
     */
    public CopilotProperties getProperties() {
        return properties;
    }
    
    /**
     * Raw fields in declaration order, used to patch a document without dropping what the
     * request does not submit.
     *
     * @return unmodifiable raw fields
     */
    public Map<String, Object> getFields() {
        return fields;
    }
}
