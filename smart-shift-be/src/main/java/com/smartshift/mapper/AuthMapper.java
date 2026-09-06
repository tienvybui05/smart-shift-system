package com.smartshift.mapper;

import com.smartshift.dto.auth.CurrentUserResponse;
import com.smartshift.entity.Position;
import com.smartshift.entity.User;
import org.springframework.stereotype.Component;

@Component
public class AuthMapper {

    public CurrentUserResponse toCurrentUserResponse(User user) {
        Position position = user.getPosition();
        return new CurrentUserResponse(
            user.getId(),
            user.getEmployeeCode(),
            user.getUsername(),
            user.getFullName(),
            user.getRole().getName(),
            user.getLocation().getId(),
            user.getLocation().getName(),
            position == null ? null : position.getId(),
            position == null ? null : position.getName()
        );
    }
}
