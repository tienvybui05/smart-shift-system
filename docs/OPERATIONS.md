# CI, triển khai Docker và giám sát hệ thống

## Pipeline GitHub Actions

Đã bổ sung JaCoCo, quality gate tổng hợp 71% và E2E Chromium trong CI. Kết quả, phạm vi coverage và cách chạy: [TESTING_AND_COVERAGE.md](TESTING_AND_COVERAGE.md).

Workflow chính là `.github/workflows/ci.yml`, chạy trên push vào `main`/`master`, pull request vào hai nhánh này và khi bấm **Run workflow**. Hai workflow backend/frontend được gọi lại từ workflow chính, không chạy trùng mỗi lượt push.

| Job | Các bước bắt buộc |
|---|---|
| Backend | Java 17, PostgreSQL 17 riêng, `./mvnw -B --no-transfer-progress clean verify`: unit test, Spring context, kiểm tra quyền giám sát, Flyway/validate schema và đóng gói JAR |
| Frontend | Node 22, `npm ci`, ESLint toàn project, Vite production build |
| Integration | Validate Compose/Prometheus, test alert rule, build image, đợi QA healthy, kiểm tra monitoring API, điều chỉnh công và lịch kéo thả; kiểm tra luồng deploy/rollback bằng Docker mock |

Test lỗi làm job thất bại; không còn `continue-on-error` và không chạy bước build bỏ test để che lỗi. Job integration chỉ build/chạy QA sau khi cả backend và frontend kiểm tra đạt. Workflow có timeout, quyền `contents: read`, tự hủy lần chạy cũ cùng nhánh. Nhánh PR và main đều qua cùng bộ kiểm tra. Nên đặt workflow thành required check trong branch protection của repo.

Không có bước publish registry hay upload dữ liệu QA/log/artifact. Kết quả pass/fail hiển thị trong từng job; dữ liệu fixture chỉ tồn tại trong runner tạm thời của lượt kiểm tra. Pipeline đã được chuẩn bị trong repo; phải commit/push mới có lần chạy thật trên GitHub.

## Health check và quyền truy cập

| Endpoint | Quyền | Ý nghĩa |
|---|---|---|
| `GET /livez`, `/actuator/health/liveness` | Công khai | Tiến trình và trạng thái sống của ứng dụng; không phụ thuộc DB |
| `GET /readyz`, `/actuator/health/readiness` | Công khai | Sẵn sàng phục vụ, gồm trạng thái ứng dụng và kết nối DB |
| `GET /actuator/health` | Công khai, chỉ trạng thái | Admin JWT được xem thêm thành phần/chi tiết; người khác không thấy cấu hình DB/ổ đĩa |
| `GET /actuator/info`, `/actuator/metrics/**` | Admin JWT | Chẩn đoán ứng dụng và chỉ số nội bộ |
| `GET /actuator/prometheus` | Admin JWT khi chưa bật monitoring; tài khoản scrape riêng khi đã bật | Dữ liệu cho Prometheus |
| `GET /healthz` trên frontend | Công khai | Nginx đang đáp ứng; không thay thế readiness backend |

Image backend dùng readiness probe, image frontend dùng Nginx probe. Compose chỉ khởi động frontend sau backend healthy. `restart: unless-stopped` khởi động lại tiến trình bị thoát; trạng thái Docker `unhealthy` tự nó không khiến Docker restart container.

Khi bật monitoring, tài khoản `prometheus` dùng HTTP Basic chỉ có quyền GET endpoint scrape. Nó không phải user trong DB, không đăng nhập vào ứng dụng, không đọc API nghiệp vụ hoặc endpoint Actuator khác. Mật khẩu đọc từ file Docker secret, không dùng JWT người quản trị và không in vào log.

## Bật Prometheus và Grafana ở QA

```powershell
python qa-automation/scripts/setup_monitoring.py
docker compose -f compose.attendance-test.yml -f compose.monitoring.yml up -d --build --wait --wait-timeout 180
python qa-automation/scripts/monitoring_e2e.py --stack
```

Script tạo ngẫu nhiên hai mật khẩu trong `.secrets/`, không ghi đè khi chạy lại. Thư mục này được Git ignore; không chia sẻ/commit file. Trên Linux, thư mục chỉ chủ sở hữu được truy cập; file có thể đọc bởi UID của các container được mount đúng file đó.

- Ứng dụng QA: http://localhost:23000.
- Prometheus: http://localhost:29090, trang **Targets** xem trạng thái scrape, **Alerts** xem cảnh báo.
- Grafana: http://localhost:23001, user `admin`. Xem mật khẩu cục bộ bằng `Get-Content .secrets/grafana_admin_password` rồi đăng nhập.
- Dashboard **Smart Shift · Giám sát hệ thống** được provision sẵn trong folder **Smart Shift**: backend UP/DOWN, số request/giây, tỷ lệ HTTP 5xx, độ trễ p95, JVM heap, CPU, pool kết nối DB và request theo mã HTTP.
- Grafana/Prometheus chỉ bind `127.0.0.1`; Prometheus UI không có đăng nhập, không mở cổng này ra Internet. Nếu cần truy cập từ xa, dùng tunnel hoặc proxy có TLS và xác thực.

Prometheus scrape mỗi 15 giây, giữ dữ liệu 7 ngày. Dashboard cần một vài lần scrape và có lưu lượng để đồ thị rate/p95 xuất hiện. Không thu thập nội dung request, token, thông tin nhân viên hoặc lương vào metrics.

Năm cảnh báo: backend không scrape được trong 1 phút, lỗi 5xx trên 5% trong 5 phút, p95 trên 1 giây trong 5 phút, heap trên 85% trong 10 phút, pool DB có luồng chờ trong 2 phút. Cảnh báo xuất hiện trong Prometheus; chưa có Alertmanager gửi email/Lark và chưa tự restart ứng dụng.

Để bật cho stack chính, thay file `compose.attendance-test.yml` bằng `docker-compose.yml`. Hai stack không dùng chung volume DB hay volume metrics; nếu chạy đồng thời cả hai, đặt `PROMETHEUS_PORT`/`GRAFANA_PORT` khác nhau cho một stack. Overlay monitoring không thay định nghĩa DB hoặc xóa dữ liệu. Dừng bằng cùng danh sách file với `down`, không dùng `-v` khi cần giữ dữ liệu.

## Triển khai bản đã kiểm tra trên máy/server Docker

`ops/deploy.ps1` thực hiện triển khai tại máy đang chạy script, không kết nối hoặc gửi mã nguồn sang server/registry khác. Cần PowerShell, Docker Compose, `.env` của môi trường đích và các bài kiểm tra CI đã đạt trước khi triển khai.

```powershell
# Dùng tag theo commit đã kiểm tra; build cả hai image trước khi thay container.
$releaseTag = git rev-parse --short=12 HEAD
./ops/deploy.ps1 -Version $releaseTag -Build -Monitoring

# Tái triển khai image cục bộ đã có, không build lại:
./ops/deploy.ps1 -Version $releaseTag -Monitoring
```

Image được gắn tag `smart-shift/backend:<version>` và `smart-shift/frontend:<version>`. Compose release yêu cầu chỉ định rõ hai image. Script kiểm tra cấu hình, đợi readiness tối đa 180 giây, giữ nguyên volume DB. Nếu triển khai lỗi và có cả hai container trước đó, script áp lại đúng hai image cũ và vẫn trả lỗi để người vận hành biết bản mới thất bại. Lần đầu chưa có bản cũ thì chỉ báo lỗi, không giả báo đã rollback.

Rollback image không đảo ngược Flyway migration hoặc phục hồi DB. Cần backup DB trước khi triển khai và migration tương thích ngược nếu muốn quay lui ứng dụng. Script không tự xóa volume, không `down -v`, không sửa password DB và không can thiệp stack QA khi triển khai stack chính.

Việc tự publish image sang registry và tự triển khai lên hạ tầng bên ngoài chưa được bật. Cấu hình publish GHCR và upload kết quả QA/log đã bị xét duyệt tự động từ chối do chưa có ủy quyền xuất dữ liệu; phiên bản này dùng kiểm tra CI và quy trình triển khai Docker cục bộ có kiểm soát.

## Tự kiểm tra

```powershell
./qa-automation/scripts/test_deploy.ps1
python qa-automation/scripts/monitoring_e2e.py --stack
docker compose -f compose.attendance-test.yml -f compose.monitoring.yml ps
```

`test_deploy.ps1` dùng Docker mock, kiểm tra build trước deploy, chờ healthy, khôi phục môi trường shell, rollback hai image khi readiness thất bại và trả lỗi sau rollback. Nó không đổi container thật. Kiểm tra monitoring dùng API thật trên DB `attendance_qa` và chỉ thêm fixture QA.

Có thể chạy lại kiểm tra giao diện Grafana bằng `node qa-automation/scripts/monitoring_browser.cjs` với Playwright/Chromium đã cài. Dùng `PLAYWRIGHT_MODULE` để trỏ đến module Playwright ngoài node_modules mặc định và `CHROME_PATH` để chỉ định executable khi dùng playwright-core. Script chỉ đọc mật khẩu cục bộ, không ghi mật khẩu vào kết quả hoặc ảnh chụp.

Tham khảo: [Spring Boot Actuator](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html), [Prometheus configuration](https://prometheus.io/docs/prometheus/latest/configuration/configuration/), [Grafana provisioning](https://grafana.com/docs/grafana/latest/administration/provisioning/).

## Kiểm chứng ngày 05/11/2026

- `clean verify` trên Java 17/Linux và DB mới `smart_shift_ops_ci`: **146 test đạt**, không bỏ qua test; Flyway áp dụng đủ 25 migration từ DB trống.
- 8 test HTTP tích hợp xác nhận health/probe, readiness 503 khi từ chối phục vụ nhưng liveness vẫn 200, quyền Admin/Manager, xác thực scrape và giới hạn tài khoản metrics. 3 test bổ sung kiểm tra file secret thiếu/quá ngắn và xuống dòng cuối file.
- Sáu kiểm tra luồng triển khai/quay lui đạt, không thay container thật.
- Actionlint đạt cho cả ba workflow; ESLint toàn bộ frontend đạt.
- Promtool xác thực cấu hình và đủ năm rule; test mô phỏng xác nhận cảnh báo backend chờ đúng thời gian, backend khỏe không báo lỗi, cùng bốn loại cảnh báo HTTP/heap/pool.
- **20 kiểm tra monitoring API đạt** với backend, Prometheus và Grafana chạy thật: phân quyền, mật khẩu scrape, target UP, năm rule được nạp và dashboard tám panel. **55 kiểm tra hồi quy API đạt** cho công/lương (30) và lịch kéo thả (25).
- **5 kiểm tra Chromium đạt**: đăng nhập Grafana, nạp đủ tám panel, trạng thái UP, tất cả panel có dữ liệu thật và không có lỗi JavaScript. Ảnh dashboard lưu cục bộ ở `qa-automation/results/monitoring-grafana.png`; không upload ra ngoài.
- Đây là kiểm chứng cục bộ cho các bước CI. Chưa có kết quả chạy GitHub Actions trước khi commit/push thay đổi.
