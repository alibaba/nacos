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

package com.alibaba.nacos.console.model.button;

/**
 * 开关返回结果.
 * @author 985492783@qq.com
 * @date 2023/3/28 10:27
 */
public class SwitchResult {
    
    private final Integer code;
    
    private final String message;
    
    public SwitchResult(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
    
    public static SwitchResult nonSwitchFail() {
        return new SwitchResult(400, "配置不能改变");
    }
    
    public static SwitchResult nonSameClassFail() {
        return new SwitchResult(401, "参数类型与所需类型不符");
    }
}
