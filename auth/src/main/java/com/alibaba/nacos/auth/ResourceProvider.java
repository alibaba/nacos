package com.alibaba.nacos.auth;

import java.util.Collections;

/**
 * provide user resource.
 */
public interface ResourceProvider {
    
    Collections AUTHORITYRESOURCES = null;
    
    /**
     * get user Resource.
     * @return Collecions reources cllection
     */
    Collections getAuthorityResource();
    
    /**
     * set user Resource.
     * @param resources authorityResources
     */
    void setAuthorityResources(Collections resources);
}
