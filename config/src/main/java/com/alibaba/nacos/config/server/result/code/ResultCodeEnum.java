/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.result.code;

import com.alibaba.nacos.common.model.core.IResultCode;

/**
 * ResultCodeEnum.
 *
 * @author klw
 * @ClassName: ResultCodeEnum
 * @Description: result code enum
 * @date 2019/6/28 14:43
 */
@Deprecated
public enum ResultCodeEnum implements IResultCode {
    
    /**
     * Common code.
     **/
    SUCCESS(200, "处理成功"),
    ERROR(500, "服务器内部错误"),
    
    /**
     * Config use 100001 ~ 100999.
     **/
    NAMESPACE_NOT_EXIST(100001, "目标 namespace 不存在"),
    
    METADATA_ILLEGAL(100002, "导入的元数据非法"),
    
    DATA_VALIDATION_FAILED(100003, "未读取到合法数据"),
    
    PARSING_DATA_FAILED(100004, "解析数据失败"),
    
    DATA_EMPTY(100005, "导入的文件数据为空"),
    
    NO_SELECTED_CONFIG(100006, "没有选择任何配置");
    
    private int code;
    
    private String msg;
    
    ResultCodeEnum(int code, String codeMsg) {
        this.code = code;
        this.msg = codeMsg;
    }
    
    @Override
    public int getCode() {
        return code;
    }
    
    @Override
    public String getCodeMsg() {
        return msg;
    }
}
