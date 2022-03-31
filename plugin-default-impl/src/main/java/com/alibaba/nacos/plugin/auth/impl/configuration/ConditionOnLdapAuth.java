package com.alibaba.nacos.plugin.auth.impl.configuration;

import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthSystemTypes;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * when nacos.core.auth.system.type=ldap
 *
 * @author karson
 */
public class ConditionOnLdapAuth implements Condition {
    @Override
    public boolean matches(ConditionContext conditionContext, AnnotatedTypeMetadata annotatedTypeMetadata) {
        return AuthSystemTypes.LDAP.name().equalsIgnoreCase(EnvUtil.getProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE));
    }
}
