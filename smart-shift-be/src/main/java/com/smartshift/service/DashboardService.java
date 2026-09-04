package com.smartshift.service;

import com.smartshift.dto.dashboard.DashboardResponse;

public interface DashboardService {

    DashboardResponse getDashboard(String username);
}
