package com.alibaba.nacos.config.server.auth;

import com.alibaba.nacos.config.server.modules.entity.Permissions;
import com.alibaba.nacos.config.server.modules.entity.QPermissions;
import com.alibaba.nacos.config.server.modules.repository.PermissionsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class PermissionPersistServiceTmp {

    @Autowired
    private PermissionsRepository permissionsRepository;

    public Page<Permissions> getPermissions(String role, int pageNo, int pageSize) {
        return permissionsRepository.findAll(QPermissions.permissions.role.eq(role),
            PageRequest.of(pageNo, pageSize));
    }

    public void addPermission(String role, String resource, String action) {
        permissionsRepository.save(new Permissions(role, resource, action));
    }


    public void deletePermission(String role, String resource, String action) {
        QPermissions qPermissions = QPermissions.permissions;
        permissionsRepository.findOne(qPermissions.role.eq(role)
            .and(qPermissions.resource.eq(resource))
            .and(qPermissions.action.eq(action)))
            .ifPresent(p -> permissionsRepository.delete(p));
    }
}
