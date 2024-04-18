/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.logger.adapter.logback12;

import ch.qos.logback.core.joran.action.Action;
import ch.qos.logback.core.joran.action.ActionUtil;
import ch.qos.logback.core.joran.spi.ActionException;
import ch.qos.logback.core.joran.spi.InterpretationContext;
import ch.qos.logback.core.util.OptionHelper;
import com.alibaba.nacos.common.logging.NacosLoggingProperties;
import org.xml.sax.Attributes;

/**
 * support logback read properties from NacosClientProperties. just like springProperty. for example:
 * <nacosClientProperty scope="context" name="logPath" source="system.log.path" defaultValue="/root" />
 *
 * @author onewe
 */
class NacosClientPropertyAction extends Action {
    
    private static final String DEFAULT_VALUE_ATTRIBUTE = "defaultValue";
    
    private static final String SOURCE_ATTRIBUTE = "source";
    
    private final NacosLoggingProperties loggingProperties;
    
    NacosClientPropertyAction(NacosLoggingProperties loggingProperties) {
        this.loggingProperties = loggingProperties;
    }
    
    @Override
    public void begin(InterpretationContext ic, String elementName, Attributes attributes) throws ActionException {
        String name = attributes.getValue(NAME_ATTRIBUTE);
        String source = attributes.getValue(SOURCE_ATTRIBUTE);
        ActionUtil.Scope scope = ActionUtil.stringToScope(attributes.getValue(SCOPE_ATTRIBUTE));
        String defaultValue = attributes.getValue(DEFAULT_VALUE_ATTRIBUTE);
        if (OptionHelper.isEmpty(name)) {
            addError("The \"name\" and \"source\"  attributes of <nacosClientProperty> must be set");
        }
        ActionUtil.setProperty(ic, name, getValue(source, defaultValue), scope);
    }
    
    @Override
    public void end(InterpretationContext ic, String name) throws ActionException {
    
    }
    
    private String getValue(String source, String defaultValue) {
        return null == loggingProperties ? defaultValue : loggingProperties.getValue(source, defaultValue);
    }
}