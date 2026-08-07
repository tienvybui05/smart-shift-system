package com.smartshift.mapper;

import com.smartshift.dto.role.RoleResponse;
import com.smartshift.entity.Role;
import org.springframework.stereotype.Component;

@Component
public class RoleMapper {

    public RoleResponse toResponse(Role role) {
        return new RoleResponse(
            role.getId(),
            role.getName(),
            role.getDescription()
        );
    }
}

