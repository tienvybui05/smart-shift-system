package com.smartshift.service;

import com.smartshift.dto.role.RoleResponse;

import java.util.List;

public interface RoleService {

    List<RoleResponse> getAllRoles();

    RoleResponse getRoleById(Long id);
}

