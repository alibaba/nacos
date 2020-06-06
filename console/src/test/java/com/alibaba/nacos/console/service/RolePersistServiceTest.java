package com.alibaba.nacos.console.service;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.auth.RolePersistServiceTmp;
import com.alibaba.nacos.config.server.modules.entity.Roles;
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
 * @version $Id: RolePersistServiceTest.java,v 0.1 2020年06月06日 14:59 $Exp
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class RolePersistServiceTest extends BaseTest {

    private Roles roles;

    @Before
    public void before() {
        String data = readClassPath("test-data/roles.json");
        roles = JacksonUtils.toObj(data, Roles.class);
    }


    @Autowired
    private RolePersistServiceTmp rolePersistServiceTmp;

    @Test
    public void getRolesTest() {
        Page<Roles> page = rolePersistServiceTmp.getRoles(0, 10);
        Assert.assertNotNull(page.getContent());
        Assert.assertTrue(page.getContent().size() > 0);
    }

    @Test
    public void getRolesByUserNameTest() {
        Page<Roles> page = rolePersistServiceTmp.getRolesByUserName(roles.getUsername(), 0, 10);
        Assert.assertNotNull(page.getContent());
        Assert.assertTrue(page.getContent().size() > 0);
    }

    @Test
    public void addRoleTest() {
        rolePersistServiceTmp.addRole(roles.getRole(), roles.getUsername());
    }


    @Test
    public void deleteRole1Test() {
        rolePersistServiceTmp.deleteRole(roles.getRole());
    }

    @Test
    public void deleteRole2Test() {
        rolePersistServiceTmp.deleteRole(roles.getRole(), roles.getUsername());
    }

}
