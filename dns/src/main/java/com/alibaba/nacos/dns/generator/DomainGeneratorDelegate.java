package com.alibaba.nacos.dns.generator;

import com.alibaba.nacos.naming.core.Service;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DomainGeneratorDelegate implements ApplicationContextAware, InitializingBean {

    private ApplicationContext applicationContext;
    private final List<DomainGenerator> generators = new ArrayList<>();

    public List<String> create(Service service) {
        List<String> domainNames = new ArrayList<>(generators.size());
        for (DomainGenerator domainGenerator : generators) {
            if (domainGenerator.isMatch(service)) {
                domainNames.add(domainGenerator.create(service));
            }
        }
        return domainNames;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        generators.addAll(applicationContext.getBeansOfType(DomainGenerator.class).values());
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
