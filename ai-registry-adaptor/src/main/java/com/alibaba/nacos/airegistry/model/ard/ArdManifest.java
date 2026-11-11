/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.airegistry.model.ard;

import java.util.ArrayList;
import java.util.List;

/**
 * Host discovery manifest. Legacy catalog envelope fields are intentionally absent.
 *
 * @author nacos
 */
public class ArdManifest {
    
    private List<ArdEntry> entries = new ArrayList<>();
    
    public List<ArdEntry> getEntries() {
        return entries;
    }
    
    public void setEntries(List<ArdEntry> entries) {
        this.entries = entries;
    }
}
