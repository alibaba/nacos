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

package org.springframework.boot.autoconfigure.data.jpa;

import org.springframework.boot.autoconfigure.data.AbstractRepositoryConfigurationSourceSupport;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.jpa.repository.config.JpaRepositoryConfigExtension;
import org.springframework.data.repository.config.BootstrapMode;
import org.springframework.data.repository.config.RepositoryConfigurationExtension;
import org.springframework.data.util.Streamable;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.util.Locale;

/**
 * JpaRepositoriesAutoConfigureRegistrar.
 *
 * @author Nacos
 */
class JpaRepositoriesAutoConfigureRegistrar extends AbstractRepositoryConfigurationSourceSupport {
    
    private BootstrapMode bootstrapMode = null;
    
    @Override
    protected Class<? extends Annotation> getAnnotation() {
        return EnableJpaRepositories.class;
    }
    
    @Override
    protected Class<?> getConfiguration() {
        return JpaRepositoriesAutoConfigureRegistrar.EnableJpaRepositoriesConfiguration.class;
    }
    
    @Override
    protected RepositoryConfigurationExtension getRepositoryConfigurationExtension() {
        return new JpaRepositoryConfigExtension();
    }
    
    @Override
    protected BootstrapMode getBootstrapMode() {
        return (this.bootstrapMode == null) ? super.getBootstrapMode() : this.bootstrapMode;
    }
    
    @Override
    public void setEnvironment(Environment environment) {
        super.setEnvironment(environment);
        configureBootstrapMode(environment);
    }
    
    private void configureBootstrapMode(Environment environment) {
        String property = environment.getProperty("spring.data.jpa.repositories.bootstrap-mode");
        if (StringUtils.hasText(property)) {
            this.bootstrapMode = BootstrapMode.valueOf(property.toUpperCase(Locale.ENGLISH));
        }
    }
    
    @Override
    protected Streamable<String> getBasePackages() {
        return Streamable.of("com.alibaba.nacos.config.server.modules.repository");
    }
    
    @EnableJpaRepositories
    private static class EnableJpaRepositoriesConfiguration {
    
    }
    
}
