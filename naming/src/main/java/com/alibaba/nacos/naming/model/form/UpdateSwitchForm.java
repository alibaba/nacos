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

package com.alibaba.nacos.naming.model.form;

import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;
import org.springframework.http.HttpStatus;

import java.io.Serializable;
import java.util.Objects;

/**
 * UpdateSwitchForm.
 * @author dongyafei
 * @date 2022/9/15
 */
public class UpdateSwitchForm implements Serializable {
    
    private static final long serialVersionUID = -1580959130954136990L;
    
    private Boolean debug;
    
    private String entry;
    
    private String value;
    
    public UpdateSwitchForm() {
    }
    
    /**
     * check param.
     *
     * @throws NacosApiException NacosApiException
     */
    public void validate() throws NacosApiException {
        if (StringUtils.isBlank(entry)) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_MISSING,
                    "Required parameter 'entry' type String is not present");
        }
        if (StringUtils.isBlank(value)) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_MISSING,
                    "Required parameter 'value' type String is not present");
        }
    }
    
    public Boolean getDebug() {
        return debug;
    }
    
    public void setDebug(Boolean debug) {
        this.debug = debug;
    }
    
    public String getEntry() {
        return entry;
    }
    
    public void setEntry(String entry) {
        this.entry = entry;
    }
    
    public String getValue() {
        return value;
    }
    
    public void setValue(String value) {
        this.value = value;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UpdateSwitchForm that = (UpdateSwitchForm) o;
        return Objects.equals(debug, that.debug) && Objects.equals(entry, that.entry) && Objects
                .equals(value, that.value);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(debug, entry, value);
    }
    
    @Override
    public String toString() {
        return "UpdateSwitchForm{" + "debug=" + debug + ", entry='" + entry + '\'' + ", value='" + value + '\'' + '}';
    }
}
