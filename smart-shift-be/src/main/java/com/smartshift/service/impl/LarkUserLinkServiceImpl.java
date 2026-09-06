package com.smartshift.service.impl;

import com.smartshift.config.LarkProperties;
import com.smartshift.dto.common.PageResponse;
import com.smartshift.dto.lark.LarkUserLinkResponse;
import com.smartshift.dto.lark.LarkUserSyncSummaryResponse;
import com.smartshift.entity.User;
import com.smartshift.exception.BusinessRuleException;
import com.smartshift.exception.ResourceNotFoundException;
import com.smartshift.integration.lark.LarkAppClient;
import com.smartshift.integration.lark.LarkUserLookupResult;
import com.smartshift.repository.LarkDeliveryLogRepository;
import com.smartshift.repository.UserRepository;
import com.smartshift.service.LarkUserLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LarkUserLinkServiceImpl implements LarkUserLinkService {

    private final UserRepository userRepository;
    private final LarkDeliveryLogRepository deliveryRepository;
    private final LarkAppClient larkAppClient;
    private final LarkProperties properties;
    private final Clock clock;

    @Override
    public PageResponse<LarkUserLinkResponse> getUsers(int page, int size) {
        Page<User> users = userRepository.findAllDetailed(
            PageRequest.of(page, size)
        );
        return PageResponse.from(users.map(this::toResponse));
    }

    @Override
    @Transactional
    public LarkUserLinkResponse syncUser(Long userId) {
        requirePersonalApp();
        User user = userRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> userNotFound(userId));
        if (!user.isActive()) {
            throw new BusinessRuleException(
                "Không thể đồng bộ tài khoản nhân viên đã bị khóa"
            );
        }
        synchronize(user);
        return toResponse(user);
    }

    @Override
    @Transactional
    public LarkUserSyncSummaryResponse syncAllActiveUsers() {
        requirePersonalApp();
        List<User> users = userRepository.findAllByActiveTrueOrderByFullNameAsc();
        int linked = 0;
        int failed = 0;
        for (User user : users) {
            if (synchronize(user)) {
                linked++;
            } else {
                failed++;
            }
        }
        return new LarkUserSyncSummaryResponse(users.size(), linked, failed);
    }

    private boolean synchronize(User user) {
        LarkUserLookupResult result = larkAppClient.findUser(
            user.getEmail(),
            user.getPhoneNumber()
        );
        user.setLarkSyncedAt(clock.instant());
        if (!result.success()) {
            user.setLarkSyncError(limit(result.error(), 500));
            userRepository.save(user);
            return false;
        }

        User linkedUser = userRepository.findByLarkOpenId(result.openId())
            .orElse(null);
        if (linkedUser != null && !linkedUser.getId().equals(user.getId())) {
            user.setLarkOpenId(null);
            user.setLarkSyncError(
                "Tài khoản Lark này đã liên kết với "
                    + linkedUser.getEmployeeCode()
            );
            userRepository.save(user);
            return false;
        }

        user.setLarkOpenId(result.openId());
        user.setLarkSyncError(null);
        userRepository.save(user);
        deliveryRepository.reactivateFailedPersonalDeliveries(
            user.getId(),
            result.openId()
        );
        return true;
    }

    private void requirePersonalApp() {
        if (!properties.personalReady()) {
            throw new BusinessRuleException(
                "Lark App Bot chưa được bật hoặc chưa có App ID/App Secret"
            );
        }
    }

    private LarkUserLinkResponse toResponse(User user) {
        return new LarkUserLinkResponse(
            user.getId(),
            user.getEmployeeCode(),
            user.getFullName(),
            user.getEmail(),
            user.getPhoneNumber(),
            user.getRole().getName(),
            user.getLocation().getName(),
            user.isActive(),
            user.getLarkOpenId() != null,
            user.getLarkOpenId(),
            user.getLarkSyncedAt(),
            user.getLarkSyncError()
        );
    }

    private String limit(String value, int maximumLength) {
        if (value == null || value.length() <= maximumLength) {
            return value;
        }
        return value.substring(0, maximumLength);
    }

    private ResourceNotFoundException userNotFound(Long id) {
        return new ResourceNotFoundException(
            "Không tìm thấy nhân viên có id " + id
        );
    }
}
