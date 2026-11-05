package com.smartshift.config;

import java.util.*;
import static java.util.Map.entry;

/** Human descriptions only; springdoc reads routes, parameters and DTO schemas from Spring itself. */
final class ApiDocumentationCatalog {
    record Group(String tag, Map<String, String> summaries) {}
    private ApiDocumentationCatalog() {}
    private static Group group(String tag, String lines) {
        Map<String,String> summaries = new LinkedHashMap<>();
        lines.lines().filter(line -> !line.isBlank()).forEach(line -> {
            String[] pair = line.strip().split("\\|", 2);
            summaries.put(pair[0], pair[1]);
        });
        return new Group(tag, Map.copyOf(summaries));
    }
    static final Map<String,Group> GROUPS = Map.ofEntries(
        entry("AuthController", group("01 · Xác thực", """
            login|Đăng nhập và nhận JWT
            getCurrentUser|Xem tài khoản đang đăng nhập
            """)),
        entry("UserController", group("02 · Nhân viên", """
            getUsers|Tìm kiếm và phân trang nhân viên
            getUserById|Xem chi tiết nhân viên
            createUser|Tạo tài khoản nhân viên hoặc quản lý
            updateUser|Cập nhật hồ sơ tài khoản
            updateStatus|Bật hoặc tắt tài khoản
            updateEmployeeWorkProfile|Quản lý cập nhật hồ sơ làm việc của nhân viên
            changePassword|Đổi mật khẩu của mình
            resetPassword|Admin đặt lại mật khẩu
            """)),
        entry("LocationController", group("03 · Chi nhánh", """
            getAllLocations|Danh sách chi nhánh
            getLocationById|Chi tiết chi nhánh
            createLocation|Tạo chi nhánh
            updateLocation|Cập nhật chi nhánh và chính sách chấm công
            """)),
        entry("PositionController", group("04 · Vị trí và vai trò", """
            getAllPositions|Danh sách vị trí công việc
            getPositionById|Chi tiết vị trí công việc
            createPosition|Tạo vị trí công việc
            updatePosition|Cập nhật vị trí công việc
            """)),
        entry("RoleController", group("04 · Vị trí và vai trò", """
            getAllRoles|Danh sách vai trò
            getRoleById|Chi tiết vai trò
            """)),
        entry("ShiftTemplateController", group("05 · Mẫu ca", """
            getShiftTemplates|Tìm mẫu ca theo chi nhánh và trạng thái
            getShiftTemplateById|Chi tiết mẫu ca
            createShiftTemplate|Tạo mẫu ca
            updateShiftTemplate|Cập nhật mẫu ca
            updateStatus|Bật hoặc tắt mẫu ca
            """)),
        entry("EmployeeAvailabilityController", group("06 · Lịch rảnh", """
            getMyAvailabilities|Xem lịch rảnh của mình
            getUserAvailabilities|Admin tra cứu lịch rảnh của nhân viên
            createMyAvailability|Đăng ký khoảng rảnh hoặc bận
            updateMyAvailability|Cập nhật khoảng rảnh hoặc bận
            deleteMyAvailability|Xóa khoảng rảnh hoặc bận
            """)),
        entry("TimeOffController", group("07 · Nghỉ phép", """
            getMyRequests|Xem đơn nghỉ của mình
            createMyRequest|Gửi đơn nghỉ
            cancelMyRequest|Hủy đơn nghỉ đang chờ
            getRequests|Tra cứu đơn nghỉ trong phạm vi quản lý
            reviewRequest|Duyệt hoặc từ chối đơn nghỉ
            """)),
        entry("SchedulePeriodController", group("08 · Kỳ lịch và công bố", """
            getSchedulePeriods|Danh sách kỳ lịch
            getSchedulePeriodById|Chi tiết kỳ lịch
            createSchedulePeriod|Tạo kỳ lịch Nháp
            updateSchedulePeriod|Cập nhật kỳ lịch
            checkPublication|Kiểm tra điều kiện công bố
            publishSchedulePeriod|Công bố lịch hợp lệ
            lockSchedulePeriod|Khóa lịch đã công bố
            """)),
        entry("WorkShiftController", group("09 · Ca làm việc", """
            getWorkShifts|Danh sách ca trong kỳ lịch
            getWorkShiftById|Chi tiết ca làm việc
            createWorkShift|Tạo ca làm việc
            generateWorkShifts|Sinh ca từ mẫu theo khoảng ngày
            updateWorkShift|Cập nhật thời gian ca
            updateStatus|Hủy hoặc mở lại ca
            """)),
        entry("ShiftRequirementController", group("10 · Nhu cầu và phân công", """
            getRequirements|Xem nhu cầu nhân sự của ca
            saveRequirements|Lưu nhu cầu theo vị trí
            """)),
        entry("ShiftAssignmentController", group("10 · Nhu cầu và phân công", """
            getMySchedule|Xem lịch làm việc của mình
            getSummary|Xem phân công và tiến độ nhu cầu của ca
            getCandidates|Danh sách ứng viên và lý do phù hợp
            assignEmployee|Phân công nhân viên thủ công
            removeAssignment|Gỡ phân công khỏi ca
            """)),
        entry("AutoScheduleController", group("11 · Tối ưu lịch và ngân sách", """
            generate|Sinh phân công bằng hybrid ALNS–CP-SAT
            """)),
        entry("SchedulingBudgetController", group("11 · Tối ưu lịch và ngân sách", """
            settings|Xem cấu hình ngân sách tuần
            update|Cập nhật ngân sách tuần của chi nhánh
            report|Chi phí dự kiến và phần vượt ngân sách
            """)),
        entry("ScheduleBoardController", group("12 · Lịch kéo thả", """
            board|Tải lịch trực quan và danh sách nhân viên
            preview|Kiểm tra trước thao tác phân công hoặc chuyển ca
            place|Lưu thao tác phân công hoặc chuyển ca nguyên tử
            """)),
        entry("OpenShiftClaimController", group("13 · Nhận ca trống", """
            getAvailableShifts|Tìm ca trống có thể đăng ký
            getMyClaims|Xem đăng ký nhận ca của mình
            createClaim|Đăng ký nhận ca trống
            cancelClaim|Hủy đăng ký đang chờ
            getClaims|Quản lý tra cứu đăng ký nhận ca
            reviewClaim|Duyệt hoặc từ chối đăng ký nhận ca
            """)),
        entry("ShiftSwapController", group("14 · Đổi và nhường ca", """
            getMyRequests|Xem yêu cầu đổi hoặc nhường ca của mình
            getAvailableGiveaways|Tìm yêu cầu nhường ca có thể nhận
            getSwapCandidates|Tìm phân công có thể trao đổi
            createRequest|Gửi yêu cầu đổi hoặc nhường ca
            respondToRequest|Người nhận chấp nhận hoặc từ chối
            cancelRequest|Hủy yêu cầu đang chờ
            getRequestsForReview|Quản lý tra cứu yêu cầu đổi ca
            reviewRequest|Duyệt hoặc từ chối đổi hoặc nhường ca
            """)),
        entry("AttendanceController", group("15 · Chấm công", """
            checkIn|Check-in bằng vị trí và thiết bị
            checkOut|Check-out ca làm việc
            getMyAttendances|Xem bảng công của mình
            getAttendances|Tra cứu bảng công trong phạm vi quản lý
            approveAttendance|Đối soát hoặc điều chỉnh trực tiếp bản công
            """)),
        entry("EmployeeDeviceController", group("16 · Thiết bị chấm công", """
            getMyDevices|Xem thiết bị đã đăng ký của mình
            getDevices|Quản lý tra cứu thiết bị
            reviewDevice|Duyệt hoặc thu hồi thiết bị
            """)),
        entry("AttendanceCorrectionController", group("17 · Điều chỉnh công", """
            mine|Xem yêu cầu điều chỉnh công của mình
            create|Gửi yêu cầu bổ sung hoặc điều chỉnh công
            cancel|Hủy yêu cầu điều chỉnh công đang chờ
            list|Quản lý tra cứu yêu cầu điều chỉnh công
            review|Duyệt hoặc từ chối điều chỉnh công
            myHistory|Xem lịch sử công của mình
            history|Quản lý xem lịch sử thay đổi bản công
            """)),
        entry("PayrollController", group("18 · Bảng lương", """
            getMyPayrollRecords|Xem bảng lương của mình
            getPayrollRecords|Tra cứu bảng lương trong phạm vi quản lý
            calculatePayroll|Tính hoặc tính lại bảng lương Nháp
            updateBonus|Cập nhật thưởng của bảng lương Nháp
            confirmPayroll|Xác nhận và khóa bản lương
            """)),
        entry("ReportExportController", group("19 · Xuất Excel", """
            attendance|Xuất bảng chấm công XLSX
            payroll|Xuất snapshot bảng lương XLSX
            """)),
        entry("NotificationController", group("20 · Thông báo", """
            getMine|Phân trang thông báo của mình
            getUnreadCount|Đếm thông báo chưa đọc
            stream|Kết nối thông báo thời gian thực SSE
            markAsRead|Đánh dấu một thông báo đã đọc
            markAllAsRead|Đánh dấu tất cả thông báo đã đọc
            """)),
        entry("ScheduleAuditController", group("21 · Lịch sử và tổng quan", """
            getAuditLogs|Tra cứu lịch sử thay đổi lịch
            """)),
        entry("DashboardController", group("21 · Lịch sử và tổng quan", """
            getDashboard|Tổng quan theo vai trò và phạm vi tài khoản
            """)),
        entry("LarkIntegrationController", group("22 · Tích hợp Lark", """
            getStatus|Trạng thái cấu hình tích hợp Lark
            getDeliveries|Phân trang lịch sử gửi Lark
            testConnection|Xếp hàng gửi tin kiểm tra nhóm Lark
            getUsers|Tra cứu liên kết tài khoản Lark
            syncUser|Đồng bộ một tài khoản với Lark
            syncAllUsers|Đồng bộ các tài khoản đang hoạt động
            testPersonalMessage|Xếp hàng gửi tin thử đến một người
            retry|Gửi lại một lần chuyển tin thất bại
            """))
    );

    static List<String> roles(String controller, String method) {
        return switch (controller) {
            case "AuthController", "DashboardController", "NotificationController" -> List.of("ADMIN", "MANAGER", "EMPLOYEE");
            case "LocationController", "RoleController", "LarkIntegrationController" -> List.of("ADMIN");
            case "PositionController" -> method.startsWith("get") ? List.of("ADMIN", "MANAGER") : List.of("ADMIN");
            case "UserController" -> switch (method) {
                case "getUsers", "getUserById" -> List.of("ADMIN", "MANAGER");
                case "updateEmployeeWorkProfile" -> List.of("MANAGER");
                case "changePassword" -> List.of("ADMIN", "MANAGER", "EMPLOYEE");
                default -> List.of("ADMIN");
            };
            case "EmployeeAvailabilityController" -> method.equals("getUserAvailabilities") ? List.of("ADMIN") : List.of("EMPLOYEE");
            case "TimeOffController" -> Set.of("getMyRequests", "createMyRequest", "cancelMyRequest").contains(method)
                ? List.of("ADMIN", "MANAGER", "EMPLOYEE") : List.of("ADMIN", "MANAGER");
            case "ShiftAssignmentController" -> method.equals("getMySchedule") ? List.of("ADMIN", "MANAGER", "EMPLOYEE") : List.of("ADMIN", "MANAGER");
            case "OpenShiftClaimController" -> Set.of("getClaims", "reviewClaim").contains(method) ? List.of("ADMIN", "MANAGER") : List.of("EMPLOYEE");
            case "ShiftSwapController" -> Set.of("getRequestsForReview", "reviewRequest").contains(method) ? List.of("ADMIN", "MANAGER") : List.of("EMPLOYEE");
            case "AttendanceController" -> Set.of("checkIn", "checkOut", "getMyAttendances").contains(method) ? List.of("EMPLOYEE") : List.of("ADMIN", "MANAGER");
            case "EmployeeDeviceController" -> method.equals("getMyDevices") ? List.of("ADMIN", "MANAGER", "EMPLOYEE") : List.of("ADMIN", "MANAGER");
            case "AttendanceCorrectionController" -> Set.of("mine", "create", "cancel", "myHistory").contains(method) ? List.of("EMPLOYEE") : List.of("ADMIN", "MANAGER");
            case "PayrollController" -> method.equals("getMyPayrollRecords") ? List.of("MANAGER", "EMPLOYEE") : List.of("ADMIN", "MANAGER");
            default -> List.of("ADMIN", "MANAGER");
        };
    }

    static final Set<String> CREATED = Set.of("AttendanceController.checkIn", "AttendanceCorrectionController.create",
        "AutoScheduleController.generate", "EmployeeAvailabilityController.createMyAvailability", "LocationController.createLocation",
        "OpenShiftClaimController.createClaim", "PositionController.createPosition", "SchedulePeriodController.createSchedulePeriod",
        "ShiftAssignmentController.assignEmployee", "ShiftSwapController.createRequest", "ShiftTemplateController.createShiftTemplate",
        "TimeOffController.createMyRequest", "UserController.createUser", "WorkShiftController.createWorkShift", "WorkShiftController.generateWorkShifts");
    static final Set<String> NO_CONTENT = Set.of("EmployeeAvailabilityController.deleteMyAvailability", "UserController.changePassword", "UserController.resetPassword");
}
