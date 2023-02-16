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

package com.alibaba.nacos.plugin.auth.impl.jwt;

import com.alibaba.nacos.common.utils.JacksonUtils;

/**
 * NacosJwtPayload.
 *
 * @author Weizhan▪Yun
 * @date 2023/1/15 21:27
 */
public class NacosJwtPayload {
    
    private String sub;
    
    private long exp = System.currentTimeMillis() / 1000L;
    
    public String getSub() {
        return sub;
    }
    
    public void setSub(String sub) {
        this.sub = sub;
    }
    
    public long getExp() {
        return exp;
    }
    
    public void setExp(long exp) {
        this.exp = exp;
    }
    
    @Override
    public String toString() {
        return JacksonUtils.toJson(this);
    }
}
