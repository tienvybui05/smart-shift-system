package com.smartshift.config;

import com.smartshift.dto.common.ApiErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.*;
import io.swagger.v3.oas.models.security.*;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.*;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {
    private static final String ERROR = "#/components/schemas/ApiErrorResponse";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Bean OpenAPI smartShiftApi() {
        var components = new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
            .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
            .description("Đăng nhập tại POST /api/auth/login, sao chép accessToken và dán vào Authorize. Chỉ dán token, không thêm chữ Bearer."));
        ModelConverters.getInstance().readAll(ApiErrorResponse.class).forEach(components::addSchemas);
        components.addResponses("BadRequest",error(400,"Bad Request","Dữ liệu không hợp lệ hoặc vi phạm quy tắc nghiệp vụ"));
        components.addResponses("Unauthorized",error(401,"Unauthorized","Sai thông tin đăng nhập, thiếu hoặc hết hạn JWT"));
        components.addResponses("Forbidden",error(403,"Forbidden","Không có quyền, khác chi nhánh hoặc không sở hữu dữ liệu"));
        components.addResponses("NotFound",error(404,"Not Found","Không tìm thấy tài nguyên"));
        components.addResponses("Conflict",error(409,"Conflict","Dữ liệu trùng, đang được tham chiếu hoặc đã thay đổi đồng thời"));
        var tags = ApiDocumentationCatalog.GROUPS.values().stream().map(ApiDocumentationCatalog.Group::tag)
            .distinct().sorted().map(name -> new Tag().name(name)).toList();
        return new OpenAPI().info(new Info().title("Smart Shift · Tài liệu API").version("1.1.0").description("""
            API quản lý nhân sự, lịch làm việc, chấm công và bảng lương.

            **Cách thử:** mở nhóm **01 · Xác thực**, dùng `POST /api/auth/login` với tài khoản của môi trường đang mở.
            Sao chép `accessToken`, bấm **Authorize** ở đầu trang và dán token. Sau đó chọn API, bấm **Try it out**, nhập dữ liệu và **Execute**.
            JWT không được lưu qua lần tải lại trang. Đổi vai trò bằng **Logout** trong hộp Authorize rồi đăng nhập tài khoản khác.

            `ADMIN`, `MANAGER`, `EMPLOYEE` có quyền khác nhau. Mỗi API ghi vai trò được gọi; service tiếp tục kiểm tra chi nhánh, chủ sở hữu và trạng thái dữ liệu.
            Swagger gọi API thật: thao tác thêm/sửa/duyệt sẽ lưu vào môi trường hiện tại. Dùng dữ liệu QA khi thực hành.

            Ngày dùng `YYYY-MM-DD`, thời điểm dùng ISO 8601 có UTC/offset; ca và kỳ được xét theo múi giờ chi nhánh.
            Tham số phân trang bắt đầu từ `page=0`. Lỗi JSON dùng chung `ApiErrorResponse`, gồm `fieldErrors` khi sai dữ liệu đầu vào.
            """)).components(components).security(List.of(new SecurityRequirement().addList("bearerAuth")))
            .servers(List.of(new Server().url("/").description("Cùng máy chủ đang mở Swagger"))).tags(tags);
    }

    @Bean OperationCustomizer describeBusinessOperations() {
        return (operation, handler) -> {
            String controller = handler.getBeanType().getSimpleName(), method = handler.getMethod().getName();
            var group = ApiDocumentationCatalog.GROUPS.get(controller);
            if (group == null) return operation;
            String summary = group.summaries().get(method);
            if (summary == null) throw new IllegalStateException("Missing API documentation: " + controller + "." + method);
            String key = controller + "." + method;
            boolean login = key.equals("AuthController.login");
            var roles = login ? List.<String>of() : ApiDocumentationCatalog.roles(controller, method);
            operation.setOperationId(controller.replace("Controller", "") + "_" + method);
            operation.setSummary(summary);
            operation.setTags(List.of(group.tag()));
            operation.setSecurity(login ? List.of() : List.of(new SecurityRequirement().addList("bearerAuth")));
            operation.addExtension("x-roles", roles);
            operation.setDescription((login ? "**Quyền:** công khai." : "**Quyền:** " + String.join(", ", roles)
                + ". Chủ sở hữu, phạm vi chi nhánh và quy tắc nghiệp vụ vẫn được kiểm tra ở server.") + details(key));
            var responses = operation.getResponses() == null ? new ApiResponses() : operation.getResponses();
            String status = ApiDocumentationCatalog.CREATED.contains(key) ? "201" : ApiDocumentationCatalog.NO_CONTENT.contains(key) ? "204" : "200";
            ApiResponse success = responses.remove("200");
            if (success == null) success = new ApiResponse();
            success.setDescription(status.equals("201") ? "Đã tạo thành công" : status.equals("204") ? "Thành công, không có nội dung trả về" : "Thành công");
            if (status.equals("204")) success.setContent(null);
            responses.addApiResponse(status, success);
            responses.addApiResponse("400", responseRef("BadRequest"));
            responses.addApiResponse("401", responseRef("Unauthorized"));
            if (!login) {
                responses.addApiResponse("403", responseRef("Forbidden"));
                responses.addApiResponse("404", responseRef("NotFound"));
                responses.addApiResponse("409", responseRef("Conflict"));
            }
            operation.setResponses(responses);
            if (controller.equals("ReportExportController")) {
                success.setContent(new Content().addMediaType(XLSX, new MediaType().schema(new StringSchema().format("binary"))));
                success.addHeaderObject("Content-Disposition", new Header().description("Tên file XLSX tải xuống").schema(new StringSchema()));
                success.addHeaderObject("Cache-Control", new Header().schema(new StringSchema().example("no-store")));
            }
            if (key.equals("NotificationController.stream")) success.setContent(new Content().addMediaType("text/event-stream",new MediaType().schema(new StringSchema())));
            if (operation.getParameters() != null) operation.getParameters().forEach(parameter -> {
                if (parameter.getDescription() == null) {
                    String description = switch (parameter.getName()) {
                        case "startDate" -> "Ngày bắt đầu YYYY-MM-DD, tính theo chi nhánh.";
                        case "endDate" -> "Ngày kết thúc YYYY-MM-DD, tính theo chi nhánh.";
                        case "locationId" -> "ID chi nhánh; Manager chỉ được thao tác chi nhánh mình quản lý.";
                        case "page" -> "Chỉ số trang, bắt đầu từ 0.";
                        case "size" -> "Số dòng mỗi trang; giới hạn theo schema.";
                        default -> parameter.getName().endsWith("Id") || parameter.getName().equals("id") ? "ID tài nguyên đã tồn tại; tra cứu danh sách trước khi gọi." : null;
                    };
                    parameter.setDescription(description);
                }
            });
            return operation;
        };
    }

    private static ApiResponse error(int status, String label, String description) {
        return new ApiResponse().description(description).content(new Content().addMediaType("application/json", new MediaType()
            .schema(new Schema<>().$ref(ERROR)).example(Map.of("timestamp", "2026-01-01T00:00:00Z", "status", status,
                "error", label, "message", description, "path", "/api/...", "fieldErrors", Map.of()))));
    }

    private static ApiResponse responseRef(String name) { return new ApiResponse().$ref("#/components/responses/" + name); }

    private static String details(String key) {
        return switch (key) {
            case "AutoScheduleController.generate" -> "\n\nHybrid ALNS–CP-SAT bổ sung phân công AUTO cho kỳ DRAFT, giữ phân công đã có. Ngân sách là mục tiêu mềm. Thời gian chạy giới hạn; không mặc định chứng minh tối ưu toàn cục. Xem optimization và cảnh báo thiếu người trong kết quả.";
            case "ScheduleBoardController.preview" -> "\n\nChỉ đọc, không giữ chỗ. Khi chuyển, gửi sourceAssignmentId, sourceWorkShiftId và expectedVersion từ board mới nhất. Phân công nguồn được loại trừ khỏi giờ/trùng ca.";
            case "ScheduleBoardController.place" -> "\n\nKiểm tra lại trong giao dịch; chỉ ca tương lai OPEN/FILLED của kỳ DRAFT. Chuyển giữ ID, tăng version và ghi audit hai ca. Nguồn có công không chuyển được; ca nguồn thiếu người được cảnh báo. Dữ liệu cũ trả 409.";
            case "AttendanceController.checkIn", "AttendanceController.checkOut" -> "\n\nGPS và thiết bị được kiểm tra cùng chính sách mạng chi nhánh. IP do server quan sát; header IP do khách tự gửi không phải chứng cứ tin cậy. Cờ mạng/thiết bị cần đối soát có thể được lưu theo chế độ linh hoạt.";
            case "AttendanceCorrectionController.create", "AttendanceCorrectionController.review" -> "\n\nCa đã kết thúc, trong 30 ngày; tối đa một đơn PENDING mỗi phân công. Giờ công chỉ đổi sau duyệt, có baseline và lịch sử. Kỳ lương đã xác nhận chặn thay đổi công.";
            case "PayrollController.confirmPayroll" -> "\n\nBị chặn khi còn điều chỉnh công PENDING hoặc sourceChanged=true. Xác nhận giữ snapshot và chặn thay đổi công thuộc kỳ.";
            case "ReportExportController.attendance", "ReportExportController.payroll" -> "\n\nTải file XLSX thật theo bộ lọc, tối đa 93 ngày và 100000 dòng. Lỗi trả JSON ApiErrorResponse. Xuất bảng lương dùng snapshot đã lưu, không tự tính lại. Không cache file.";
            case "NotificationController.stream" -> "\n\nKết nối SSE giữ mở, yêu cầu header Authorization. Khi thử trên Swagger, bấm Cancel để đóng kết nối; không có một JSON hoàn tất như các API khác.";
            case "LarkIntegrationController.testConnection", "LarkIntegrationController.testPersonalMessage", "LarkIntegrationController.retry", "LarkIntegrationController.syncUser", "LarkIntegrationController.syncAllUsers" -> "\n\nThao tác tích hợp có thể gửi dữ liệu/tin nhắn tới Lark đã cấu hình; dùng tài khoản và môi trường thử nghiệm phù hợp.";
            default -> "";
        };
    }

    @Bean OpenApiCustomizer enrichSchemasAndProbes(@Value("${app.monitoring.enabled:false}") boolean monitoring) {
        return api -> {
            api.getComponents().getSchemas().values().forEach(schema -> {
                if (schema.getProperties() == null) return;
                for (String name : List.of("password", "currentPassword", "newPassword", "confirmPassword")) {
                    var property = (Schema<?>) schema.getProperties().get(name);
                    if (property != null) { property.setFormat("password"); property.setWriteOnly(true); }
                }
            });
            example(api,"LocationRequest",Map.of("code","SWAGGER_DEMO","name","Chi nhánh thử nghiệm","timezone","Asia/Ho_Chi_Minh","latitude",10.776889,"longitude",106.700806,"attendanceRadiusMeters",200,"wifiRequired",false,"active",true));
            example(api,"AutoScheduleRequest",Map.of("schedulePeriodId",1));
            example(api,"ShiftAssignmentRequest",Map.of("userId",1,"note","Phân công thủ công từ Swagger"));
            example(api,"CalendarPlacementRequest",Map.of("userId",1,"targetWorkShiftId",1,"reason","Điều chỉnh phân công ở kỳ Nháp"));
            var login = api.getPaths().get("/api/auth/login").getPost();
            login.getRequestBody().getContent().values().forEach(media -> media.setExample(Map.of("username", "admin", "password", "nhap-mat-khau-cua-ban")));
            addProbe(api, "/actuator/health", "Health", "Tổng trạng thái; chỉ Admin JWT xem components/details.", true);
            addProbe(api, "/livez", "Liveness", "Tiến trình ứng dụng còn hoạt động; không phụ thuộc DB.", true);
            addProbe(api, "/readyz", "Readiness", "Sẵn sàng phục vụ; bao gồm kết nối DB.", true);
            var metrics = new Operation().operationId("Monitoring_metrics").tags(List.of("23 · Giám sát"))
                .summary("Danh sách metrics nội bộ").description("Chỉ Admin JWT. Chỉ số chi tiết có thể đọc qua /actuator/metrics/{name}.")
                .security(List.of(new SecurityRequirement().addList("bearerAuth")))
                .responses(new ApiResponses().addApiResponse("200", new ApiResponse().description("Danh sách tên metrics"))
                    .addApiResponse("401", error(401,"Unauthorized","Thiếu JWT")).addApiResponse("403",error(403,"Forbidden","Cần quyền Admin")));
            api.getPaths().addPathItem("/actuator/metrics", new PathItem().get(metrics));
            if (monitoring) api.getComponents().addSecuritySchemes("monitoringBasic", new SecurityScheme().type(SecurityScheme.Type.HTTP)
                .scheme("basic").description("Chỉ scrape metrics: user prometheus, mật khẩu Docker secret; không có quyền API nghiệp vụ."));
            api.getPaths().addPathItem("/actuator/prometheus", new PathItem().get(new Operation().operationId("Monitoring_prometheus")
                .tags(List.of("23 · Giám sát")).summary("Scrape metrics Prometheus")
                .description(monitoring ? "Monitoring đang bật: dùng tài khoản scrape HTTP Basic riêng." : "Monitoring đang tắt: dùng Admin JWT.")
                .security(List.of(new SecurityRequirement().addList(monitoring ? "monitoringBasic" : "bearerAuth")))
                .responses(new ApiResponses().addApiResponse("200", new ApiResponse().description("Dữ liệu Prometheus/OpenMetrics")
                    .content(new Content().addMediaType("text/plain",new MediaType().schema(new StringSchema()))))
                    .addApiResponse("401",new ApiResponse().description("Thiếu hoặc sai thông tin xác thực")))));
        };
    }

    private static void example(OpenAPI api, String schema, Object value) {
        var model = api.getComponents().getSchemas().get(schema);
        if (model != null) model.setExample(value);
    }

    private static void addProbe(OpenAPI api, String path, String id, String description, boolean publicAccess) {
        api.getPaths().addPathItem(path, new PathItem().get(new Operation().operationId("Monitoring_" + id)
            .tags(List.of("23 · Giám sát")).summary(id).description(description)
            .security(publicAccess ? List.of() : List.of(new SecurityRequirement().addList("bearerAuth")))
            .responses(new ApiResponses().addApiResponse("200",new ApiResponse().description("UP")
                .content(new Content().addMediaType("application/json",new MediaType().schema(new ObjectSchema()
                    .addProperty("status",new StringSchema())).example(Map.of("status","UP")))))
                .addApiResponse("503",new ApiResponse().description("DOWN hoặc chưa sẵn sàng")))));
    }
}
