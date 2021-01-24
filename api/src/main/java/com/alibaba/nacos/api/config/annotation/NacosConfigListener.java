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

import com.alibaba.nacos.api.annotation.NacosProperties;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.convert.NacosConfigConverter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static com.alibaba.nacos.api.common.Constants.DEFAULT_GROUP;

/**
 * Annotation that marks a method as a listener for Nacos Config change.
 *
 * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
 * @since 0.2.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface NacosConfigListener {
    
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
     * Nacos Config type.
     *
     * @return default value is {@link ConfigType#UNSET}
     */
    ConfigType type() default ConfigType.UNSET;
    
    /**
     * Specify {@link NacosConfigConverter Nacos configuraion convertor} class to convert target type instance.
     *
     * @return The implementation class of {@link NacosConfigConverter}
     */
    Class<? extends NacosConfigConverter> converter() default NacosConfigConverter.class;
    
    /**
     * The {@link NacosProperties} attribute, If not specified, it will use global Nacos Properties.
     *
     * @return the default value is {@link NacosProperties}
     */
    NacosProperties properties() default @NacosProperties;
    
    /**
     * Maximum timeout value of execution in milliseconds, which is used to prevent long-time blocking execution
     * impacting others.
     *
     * @return default value is 1000
     */
    long timeout() default 1000L;
    
}
