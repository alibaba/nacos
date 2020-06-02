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
 * JpaRepositoriesAutoConfigureRegistrar
 *
 * @author Nacos
 */
class JpaRepositoriesAutoConfigureRegistrar
    extends AbstractRepositoryConfigurationSourceSupport {

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
        return (this.bootstrapMode == null) ? super.getBootstrapMode()
            : this.bootstrapMode;
    }

    @Override
    public void setEnvironment(Environment environment) {
        super.setEnvironment(environment);
        configureBootstrapMode(environment);
    }

    private void configureBootstrapMode(Environment environment) {
        String property = environment
            .getProperty("spring.data.jpa.repositories.bootstrap-mode");
        if (StringUtils.hasText(property)) {
            this.bootstrapMode = BootstrapMode
                .valueOf(property.toUpperCase(Locale.ENGLISH));
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
