package com.alibaba.nacos.console.service;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.auth.PermissionPersistServiceTmp;
import com.alibaba.nacos.config.server.modules.entity.Permissions;
import com.alibaba.nacos.console.BaseTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @author zhangshun
 * @version $Id: PermissionPersistServiceTest.java,v 0.1 2020年06月06日 14:58 $Exp
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class PermissionPersistServiceTest extends BaseTest {

    private Permissions permissions;

    @Before
    public void before() {
        String data = readClassPath("test-data/permissions.json");
        permissions = JacksonUtils.toObj(data, Permissions.class);
    }

    @Autowired
    private PermissionPersistServiceTmp permissionPersistServiceTmp;


    @Test
    public void getPermissionsTest() {
        Page<Permissions> page = permissionPersistServiceTmp.getPermissions(permissions.getRole(), 0, 10);
        Assert.assertNotNull(page.getContent());
        Assert.assertTrue(page.getContent().size() > 0);
    }

    @Test
    public void addPermissionTest() {
        permissionPersistServiceTmp.addPermission(permissions.getRole(), permissions.getResource(), permissions.getAction());
    }


    @Test
    public void deletePermissionTest() {
        permissionPersistServiceTmp.deletePermission(permissions.getRole(), permissions.getResource(), permissions.getAction());
    }
}
