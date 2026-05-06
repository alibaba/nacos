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

package com.alibaba.nacos.config.server.model.form;

import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;
import org.springframework.http.HttpStatus;

/**
 * Nacos HTTP config form v3, use `groupName` replace `group`.
 *
 * @author xiweng.yy
 */
public class ConfigFormV3 extends ConfigForm {
    
    private static final long serialVersionUID = 1105715502736280287L;
    
    private String groupName;
    
    public String getGroupName() {
        return groupName;
    }
    
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }
    
    @Override
    public void validate() throws NacosApiException {
        if (StringUtils.isBlank(groupName)) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_MISSING,
                    "Required parameter 'groupName' type String is not present");
        }
        super.setGroup(groupName);
        super.validate();
    }
    
    /**
     * Validate for blur search API, which allow user input empty groupName and dataId to search all configs.
     *
     * @throws NacosApiException when form parameters is invalid.
     */
    public void blurSearchValidate() throws NacosApiException {
        if (null == groupName) {
            groupName = StringUtils.EMPTY;
            super.setGroup(groupName);
        }
        if (null == getDataId()) {
            setDataId(StringUtils.EMPTY);
        }
    }
}
