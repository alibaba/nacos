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

package com.alibaba.nacos.ai.form.a2a.admin;

import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.common.utils.StringUtils;

import java.io.Serial;

/**
 * Agent Card Update Form.
 *
 * @author xiweng.yy
 */
public class AgentCardUpdateForm extends AgentCardForm {
    
    @Serial
    private static final long serialVersionUID = 353698557363707304L;
    
    private boolean setAsLatest;
    
    public boolean getSetAsLatest() {
        return setAsLatest;
    }
    
    public void setSetAsLatest(boolean setAsLatest) {
        this.setAsLatest = setAsLatest;
    }
    
    @Override
    protected void fillDefaultRegistrationType() {
        // Update does not need to fill registration type
    }
    
    @Override
    protected void validateRegistrationType() throws NacosApiException {
        // Update request if no set registration type, means not change the registration type
        if (StringUtils.isEmpty(getRegistrationType())) {
            return;
        }
        super.validateRegistrationType();
    }
}
