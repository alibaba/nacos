package com.alibaba.nacos.naming.core;

import java.util.Set;

/**
 * Use a selector to implement a so called same-label-prior rule for service discovery.
 * <h2>Backgroup</h2>
 * Consider service providers are deployed in two sites i.e. site A and site B, and consumers
 * of this service provider are also deployed in site A and site B. So the consumers may want to
 * visit the service provider in current site, thus consumers in site A visit service providers
 * in site A and consumers in site B visit service providers in site B.
 * <p>
 *
 *
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
public class Selector {

    private String name;

    private Set<String> labels;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<String> getLabels() {
        return labels;
    }

    public void setLabels(Set<String> labels) {
        this.labels = labels;
    }
}
