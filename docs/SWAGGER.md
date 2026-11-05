# Xem và thử API bằng Swagger

Swagger là trang để xem backend có những API nào và gọi thử bằng dữ liệu nhập trên trình duyệt. Nhân viên/quản lý vẫn dùng giao diện Smart Shift; trang này dành cho phát triển, kiểm thử và trình bày đồ án.

## Mở trang

| Môi trường | Swagger UI | OpenAPI JSON | OpenAPI YAML |
|---|---|---|---|
| QA đang chạy | http://localhost:23000/swagger-ui/index.html | http://localhost:23000/v3/api-docs | http://localhost:23000/v3/api-docs.yaml |
| Stack chính | http://localhost:3000/swagger-ui/index.html | http://localhost:3000/v3/api-docs | http://localhost:3000/v3/api-docs.yaml |

Swagger chạy trong backend và được Nginx proxy cùng địa chỉ với ứng dụng. Có thể mở trực tiếp ở backend QA cổng `28080`, hoặc backend chính cổng `8080`. Server `/` trong tài liệu luôn trỏ về máy chủ đang mở trang; không cần CORS extension. Container Swagger riêng ở cổng `8081` được thay bằng UI tích hợp này.

Khởi động hoặc cập nhật QA:

```powershell
docker compose -f compose.attendance-test.yml -f compose.monitoring.yml up -d --build --wait --wait-timeout 180 backend frontend
```

Nếu không dùng monitoring, chỉ cần `-f compose.attendance-test.yml`. Môi trường QA dùng DB `attendance_qa`, giữ nguyên dữ liệu/volume khi build lại.

## Thử lần đầu trong 3 bước

### 1. Đăng nhập

Mở nhóm **01 · Xác thực** → **POST /api/auth/login** → **Try it out**. Thay ví dụ bằng tài khoản QA:

```json
{"username": "admin", "password": "Demo@123456"}
```

Bấm **Execute**. Kết quả `200` có `accessToken`. Sao chép phần giá trị token, bỏ dấu ngoặc kép. Ví dụ mặc định chỉ có mật khẩu mẫu, không chứa mật khẩu thật của người dùng.

Tài khoản trên chỉ dành cho QA cục bộ. Ở stack chính dùng tài khoản/mật khẩu đã thiết lập trong môi trường đó.

### 2. Cho Swagger biết token

Bấm **Authorize** ở đầu trang, chọn **bearerAuth**, dán token vừa sao chép vào ô Value, rồi **Authorize → Close**. Chỉ dán token, không tự thêm chữ `Bearer`.

Nếu monitoring bật, có thêm **monitoringBasic**. Đây là tài khoản scrape riêng của Prometheus, chỉ dùng cho `/actuator/prometheus`; không cần điền để thử API nghiệp vụ.

### 3. Gọi API được bảo vệ

Mở **GET /api/auth/me** → **Try it out → Execute**. Nếu thành công sẽ thấy tài khoản `admin`. Thử tiếp **02 · Nhân viên → GET /api/users**; Swagger gửi JWT trong header Authorization.

Để đổi vai trò, bấm **Authorize → Logout**, đăng nhập tài khoản Manager/Employee và dán token mới. Trang không lưu JWT qua reload. Tài khoản Employee gọi danh sách nhân viên sẽ nhận `403`; Manager chỉ xem dữ liệu trong phạm vi quản lý.

## Ví dụ thao tác ghi dữ liệu

Ở QA, dùng Admin, mở **03 · Chi nhánh → POST /api/locations**, nhập:

```json
{
  "code": "SWAGGER_01",
  "name": "Chi nhánh thử Swagger",
  "timezone": "Asia/Ho_Chi_Minh",
  "active": true
}
```

Execute thành công trả `201` và `id`. Gọi **GET /api/locations** sẽ thấy chi nhánh vừa tạo. Nếu mã đã có, chọn mã khác; mã trùng trả `409`. Swagger gọi API thật và các thao tác tạo/sửa/duyệt lưu vào môi trường đang mở.

Khi thử lịch, chấm công hoặc lương, tra cứu ID từ các API danh sách trước, nhập đúng ngày/chi nhánh và tuân thủ trạng thái nghiệp vụ. Ví dụ có ID `1` chỉ minh họa cấu trúc, không bảo đảm tài nguyên đó phù hợp trong DB của bạn.

## Đọc tài liệu

- 23 nhóm có tên tiếng Việt; tổng cộng **113 thao tác nghiệp vụ**, cùng **5 endpoint giám sát**. Schema request/response được sinh từ DTO và validation trong code.
- Mỗi API có tóm tắt, vai trò gọi được, tham số, body, kết quả và lỗi. Quyền HTTP không thay thế kiểm tra chủ sở hữu, chi nhánh, giờ làm hoặc trạng thái đơn ở service.
- `page` bắt đầu từ `0`; `size` và các giới hạn số/độ dài nằm trong schema.
- Ngày dùng `YYYY-MM-DD`; thời điểm dùng ISO 8601 có UTC/offset. Nghiệp vụ ca/kỳ xét theo múi giờ chi nhánh.
- Các trường mật khẩu được ghi `writeOnly`, định dạng password. Schema chỉ mô tả cấu trúc, không đọc dữ liệu người dùng hoặc lấy token từ DB.
- Export Excel khai báo MIME XLSX và schema binary. Request export lỗi trả JSON theo format chung. SSE khai báo `text/event-stream`, giữ kết nối mở; bấm Cancel khi đã thử xong.

| Mã | Cách hiểu |
|---|---|
| 200 | Thành công có kết quả |
| 201 | Đã tạo tài nguyên/phân công |
| 204 | Thành công không có body, ví dụ đổi mật khẩu |
| 400 | Sai dữ liệu hoặc vi phạm quy tắc; xem message/fieldErrors |
| 401 | Sai thông tin đăng nhập, thiếu hoặc hết hạn JWT |
| 403 | Sai vai trò, chi nhánh hoặc chủ sở hữu |
| 404 | Không thấy tài nguyên |
| 409 | Trùng dữ liệu hoặc xung đột phiên bản/thao tác đồng thời |

Một API có thể gặp nhiều lỗi nghiệp vụ dưới cùng mã `400`; `message` thực tế giải thích trường hợp cụ thể. Phản hồi lỗi chung được tái sử dụng trong `components.responses` để file tài liệu dễ rà soát.

## Nguồn tài liệu và cập nhật

- `OpenApiConfig`: JWT, server tương đối, response chung, ví dụ, Excel/SSE và các probe giám sát.
- `ApiDocumentationCatalog`: tên nhóm, tóm tắt và vai trò HTTP. Khi đổi quyền ở `SecurityConfig`, cập nhật mô tả tương ứng.
- Springdoc đọc route, tham số, enum, DTO và Bean Validation tại runtime. Kiểm thử so sánh toàn bộ route controller với tài liệu, không chỉ đếm số lượng.
- `docs/openapi.yaml`: snapshot đã export để lưu cùng phiên bản code, đọc offline hoặc import vào công cụ kiểm thử. Không sửa snapshot thay cho code sinh tài liệu.

```powershell
python -m pip install -r qa-automation/requirements-openapi.txt

# Sau khi build lại QA từ code mới:
python qa-automation/scripts/openapi_sync.py --write

# Kiểm tra snapshot khớp route/schema/response của backend đang chạy:
python qa-automation/scripts/openapi_sync.py
python qa-automation/scripts/openapi_e2e.py
```

`openapi_sync.py` chỉ đọc API và cập nhật file khi có `--write`; dùng `--base-url` nếu backend ở địa chỉ khác. Snapshot so sánh toàn bộ hợp đồng nghiệp vụ và response/schema dùng chung; quyền scrape monitoring có thể khác giữa môi trường bật/tắt overlay.

Pipeline CI cài validator, kiểm tra snapshot không bị cũ và chạy API E2E. Mismatch làm job thất bại; CI không tự ghi đè snapshot. Validator chỉ chấp nhận `$ref` nội bộ; Swagger không dùng validator bên ngoài, không lấy UI từ CDN và không upload tài liệu tự động.

## Bật/tắt Swagger

Stack chính đọc `.env`:

```dotenv
API_DOCS_ENABLED=false
```

Recreate backend để áp dụng. Khi tắt, cả tài liệu JSON/YAML và Swagger UI trả `404`; API nghiệp vụ và health check vẫn hoạt động. QA cố định biến này là `true` trong compose để phục vụ kiểm thử. Việc xem tài liệu không cấp quyền gọi API; API nghiệp vụ vẫn yêu cầu JWT và kiểm tra quyền như trước.

## Kết quả kiểm tra ngày 05/11/2026

- **155 test Java đạt**, gồm 6 test hợp đồng OpenAPI và 3 test khi tắt Swagger. Đối chiếu đầy đủ route thật, reference schema, operationId, quyền JWT, pagination, password, 201/204, JSON, XLSX và SSE.
- **22 kiểm tra API đạt** qua PostgreSQL QA, bao gồm chuẩn OpenAPI độc lập, đăng nhập, quyền Admin/Manager/Employee, validation, đổi mật khẩu 204 và tải XLSX thật.
- **9 kiểm tra Chromium đạt**: mở Swagger qua frontend, không có lỗi resolver, đăng nhập bằng Try it out, Authorize/gọi API có JWT, tạo chi nhánh 201, reload xóa JWT/nhận 401, màn hình nhỏ, không lỗi JavaScript và không gọi validator/CDN ngoài.
- Browser script `swagger_browser.cjs` dùng Playwright/Chromium, đọc cấu hình `PLAYWRIGHT_MODULE`/`CHROME_PATH` như các script browser QA khác. Chỉ dùng QA cổng 23000, thêm fixture mới, không xóa dữ liệu; không lưu JWT/mật khẩu vào kết quả/ảnh chụp.
- Kết quả và ảnh chụp ở `qa-automation/results/openapi.json`, `swagger-browser.json`, `swagger-desktop.png`, `swagger-mobile.png` (Git ignore). Chưa chạy workflow trên GitHub trước khi commit/push.

Thư viện dùng `springdoc-openapi` 3.2.0, tương thích Spring Boot 4.1 theo [bảng tương thích chính thức](https://springdoc.org/faq.html). Cấu hình UI tham khảo [Swagger UI properties](https://springdoc.org/ui-properties.html).
