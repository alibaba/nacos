package com.alibaba.nacos.common.packagescan;

import com.alibaba.nacos.api.remote.request.Request;
import junit.framework.TestCase;

import java.util.Set;

public class PackageScanTest extends TestCase {

    public void setUp() throws Exception {
        super.setUp();
    }

    public void tearDown() throws Exception {
    }

    public void testGetSubTypesOf() {
        PackageScan packageScan=new PackageScan("com.alibaba.nacos.api.naming.remote.request");
        Set<Class<Request>> subTypesOf = packageScan.getSubTypesOf(Request.class);
        assertTrue(subTypesOf.size()>0);
    }


}