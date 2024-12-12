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

package com.alibaba.nacos.config.server.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * ConfigHistoryInfo including updated info.
 */
public class ConfigHistoryInfoPair extends ConfigHistoryInfo implements Serializable {

    private String updatedMd5;

    private String updatedContent;

    public String getUpdatedMd5() {
        return updatedMd5;
    }

    public void setUpdatedMd5(String updatedMd5) {
        this.updatedMd5 = updatedMd5;
    }

    public String getUpdatedContent() {
        return updatedContent;
    }

    public void setUpdatedContent(String updatedContent) {
        this.updatedContent = updatedContent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ConfigHistoryInfoPair that = (ConfigHistoryInfoPair) o;
        return super.equals(o) && Objects.equals(updatedMd5, that.updatedMd5)
                && Objects.equals(updatedContent, that.updatedContent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), updatedMd5, updatedContent);
    }

}