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

package com.alibaba.nacos.mcpregistry.form;

import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.NacosForm;
import org.springframework.http.HttpStatus;

/**
 * Official list servers form matching MCP Registry OpenAPI spec.
 * cursor: 分页游标, 为空表示第一页；实现上使用 offset 的字符串表示。
 * limit: 返回数量，默认 30，最大 100。
 *
 * @author xinluo
 */
public class ListServersOfficialForm implements NacosForm {
    /** 默认 limit 数值. */
    public static final int DEFAULT_LIMIT = 30;

    /** 最大 limit 数值. */
    public static final int MAX_LIMIT = 100;

    /** offset 的字符串表示. */
    private String cursor;

    private Integer limit = DEFAULT_LIMIT;

    private String search;

    private String updatedSince;

    public String getCursor() {
        return cursor;
    }

    public void setCursor(String cursor) {
        this.cursor = cursor;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public String getSearch() {
        return search;
    }

    public void setSearch(String search) {
        this.search = search;
    }

    public String getUpdatedSince() {
        return updatedSince;
    }

    public void setUpdatedSince(String updatedSince) {
        this.updatedSince = updatedSince;
    }
    
    /**
     * 解析 cursor 字段为 offset 数值.
     * 当 cursor 为空时返回 0；当为非法数字时抛出 NacosApiException.
     *
     * @return offset 数值
     * @throws NacosApiException 当 cursor 非法或为负数
     */
    public int resolveOffset() throws NacosApiException {
        if (cursor == null || cursor.isEmpty()) {
            return 0;
        }
        try {
            int off = Integer.parseInt(cursor);
            if (off < 0) {
                throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_VALIDATE_ERROR, "cursor must be >= 0");
            }
            return off;
        } catch (NumberFormatException e) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_VALIDATE_ERROR, "cursor must be numeric");
        }
    }

    @Override
    public void validate() throws NacosApiException {
        if (limit == null) {
            limit = DEFAULT_LIMIT;
        }
        if (limit < 0) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_VALIDATE_ERROR, "limit must be >= 0");
        }
        if (limit > MAX_LIMIT) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_VALIDATE_ERROR, "limit must <= " + MAX_LIMIT);
        }
        // validate cursor numeric if present
        resolveOffset();
    }
}
