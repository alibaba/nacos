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

package com.alibaba.nacos.api.config.annotation;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.annotation.NacosProperties;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.config.ConfigType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static com.alibaba.nacos.api.common.Constants.DEFAULT_GROUP;

/**
 * An annotation for Nacos configuration Properties for binding POJO as Properties Object.
 *
 * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
 * @see PropertyKeyConst
 * @since 0.2.0
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NacosConfigurationProperties {
    
    /**
     * config prefix name.
     *
     * @return default value is <code>""</code>
     */
    String prefix() default "";
    
    /**
     * Nacos Group ID.
     *
     * @return default value {@link Constants#DEFAULT_GROUP};
     */
    String groupId() default DEFAULT_GROUP;
    
    /**
     * Nacos Data ID.
     *
     * @return required value.
     */
    String dataId();
    
    /**
     * set config type is yaml this method is deprecated, we support you use {@link #type()} to set config type.
     *
     * @return default value <code>false</code>
     */
    @Deprecated boolean yaml() default false;
    
    /**
     * config style.
     *
     * @return default value is {@link ConfigType#UNSET}
     */
    ConfigType type() default ConfigType.UNSET;
    
    /**
     * It indicates the properties of current doBind bean is auto-refreshed when Nacos configuration is changed.
     *
     * @return default value is <code>false</code>
     */
    boolean autoRefreshed() default false;
    
    /**
     * Flag to indicate that when binding to this object invalid fields should be ignored. Invalid means invalid
     * according to the binder that is used, and usually this means fields of the wrong type (or that cannot be coerced
     * into the correct type).
     *
     * @return the flag value (default false)
     */
    boolean ignoreInvalidFields() default false;
    
    /**
     * Flag to indicate that when binding to this object fields with periods in their names should be ignored.
     *
     * @return the flag value (default false)
     */
    boolean ignoreNestedProperties() default false;
    
    /**
     * Flag to indicate that when binding to this object unknown fields should be ignored. An unknown field could be a
     * sign of a mistake in the Properties.
     *
     * @return the flag value (default true)
     */
    boolean ignoreUnknownFields() default true;
    
    /**
     * Flag to indicate that an exception should be raised if a Validator is available and validation fails. If it is
     * set to false, validation errors will be swallowed. They will be logged, but not propagated to the caller.
     *
     * @return the flag value (default true)
     */
    boolean exceptionIfInvalid() default true;
    
    /**
     * The {@link NacosProperties} attribute, If not specified, it will use global Nacos Properties.
     *
     * @return the default value is {@link NacosProperties}
     */
    NacosProperties properties() default @NacosProperties;
    
}
