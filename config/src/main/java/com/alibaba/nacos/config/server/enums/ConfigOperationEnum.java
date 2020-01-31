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

package com.alibaba.nacos.config.server.enums;

import java.util.Objects;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public enum  ConfigOperationEnum {

    /**
     *
     */
    CONFIG_PUBLISH("config_publish"),

    /**
     *
     */
    CONFIG_TAG_PUBLISH("config_tag_publish"),

    /**
     *
     */
    CONFIG_BETA_PUBLISH("config_beta_publish"),

    /**
     *
     */
    CONFIG_REMOVE("config_remove"),

    /**
     *
     */
    CONFIG_TAG_REMOVE("config_tag_remove"),

    /**
     *
     */
    CONFIG_TAG_RELATION_PUBLISH("config_tag_relation_publish"),

    /**
     *
     */
    CONFIG_TAG_RELATION_REMOVE("config_tag_relation_remove"),

    /**
     *
     */
    CONFIG_BETA_REMOVE("config_beta_remove"),

    /**
     *
     */
    CONFIG_HISTORY_PUBLISH("config_history_publish"),

    /**
     *
     */
    CONFIG_HISTORY_REMOVE("config_history_remove"),

    /**
     *
     */
    CONFIG_AGG_PUBLISH("config_agg_publish"),

    /**
     *
     */
    CONFIG_AGG_REMOVE("config_agg_remove"),
    ;

    private String operation;

    ConfigOperationEnum(String operation) {
        this.operation = operation;
    }

    public String getOperation() {
        return operation;
    }

    public static ConfigOperationEnum sourceOf(String operation) {
        for (ConfigOperationEnum operationEnum : ConfigOperationEnum.values()) {
            if (Objects.equals(operation, operationEnum.getOperation())) {
                return operationEnum;
            }
        }
        return null;
    }
}
