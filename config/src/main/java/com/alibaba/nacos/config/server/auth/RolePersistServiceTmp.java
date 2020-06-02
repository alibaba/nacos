package com.alibaba.nacos.config.server.auth;

import com.alibaba.nacos.config.server.modules.entity.QRoles;
import com.alibaba.nacos.config.server.modules.entity.Roles;
import com.alibaba.nacos.config.server.modules.repository.RolesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class RolePersistServiceTmp {

    @Autowired
    private RolesRepository rolesRepository;

    public Page<Roles> getRoles(int pageNo, int pageSize) {
        return rolesRepository.findAll(null, PageRequest.of(pageNo, pageSize));
    }

    public Page<Roles> getRolesByUserName(String username, int pageNo, int pageSize) {
        return rolesRepository.findAll(QRoles.roles.username.eq(username), PageRequest.of(pageNo, pageSize));
    }

    public void addRole(String role, String userName) {
        rolesRepository.save(new Roles(role, userName));
    }


    public void deleteRole(String role) {
        Iterable<Roles> iterable = rolesRepository.findAll(QRoles.roles.role.eq(role));
        rolesRepository.deleteAll(iterable);
    }

    public void deleteRole(String role, String username) {
        QRoles qRoles = QRoles.roles;
        rolesRepository.findOne(qRoles.role.eq(role)
            .and(qRoles.username.eq(username)))
            .ifPresent(s -> rolesRepository.delete(s));
    }

}
