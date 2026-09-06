package com.smartshift.service;

import com.smartshift.dto.common.PageResponse;
import com.smartshift.dto.lark.LarkUserLinkResponse;
import com.smartshift.dto.lark.LarkUserSyncSummaryResponse;

public interface LarkUserLinkService {

    PageResponse<LarkUserLinkResponse> getUsers(int page, int size);

    LarkUserLinkResponse syncUser(Long userId);

    LarkUserSyncSummaryResponse syncAllActiveUsers();
}
