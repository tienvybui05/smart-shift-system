package com.smartshift.service.impl;

import com.smartshift.dto.role.RoleResponse;
import com.smartshift.entity.Role;
import com.smartshift.exception.ResourceNotFoundException;
import com.smartshift.mapper.RoleMapper;
import com.smartshift.repository.RoleRepository;
import com.smartshift.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final RoleMapper roleMapper;

    @Override
    public List<RoleResponse> getAllRoles() {
        return roleRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
            .stream()
            .map(roleMapper::toResponse)
            .toList();
    }

    @Override
    public RoleResponse getRoleById(Long id) {
        Role role = roleRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy vai trò có id " + id
            ));
        return roleMapper.toResponse(role);
    }
}

