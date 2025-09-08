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
 *
 */

package com.alibaba.nacos.ai.form.a2a.admin;

import java.io.Serial;
import java.util.Objects;

/**
 * Agent update form.
 *
 * @author KiteSoar
 */
public class AgentUpdateForm extends AgentDetailForm {
    
    @Serial
    private static final long serialVersionUID = -3213676112969078560L;
    
    private boolean setAsLatest;
    
    public boolean getSetAsLatest() {
        return setAsLatest;
    }
    
    public void setSetAsLatest(boolean setAsLatest) {
        this.setAsLatest = setAsLatest;
    }
    
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        AgentUpdateForm that = (AgentUpdateForm) o;
        return Objects.equals(setAsLatest, that.setAsLatest);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), setAsLatest);
    }
}
