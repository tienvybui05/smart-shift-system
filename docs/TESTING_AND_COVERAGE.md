# Kiểm thử, độ phủ JaCoCo và E2E trong CI

## Phạm vi và kết quả ngày 05/11/2026

Đo **code Java backend** bằng JaCoCo 0.8.16 trên toàn bundle, gồm controller, mapper, service, config và các package khác. Không cấu hình loại trừ package/class để nâng tỷ lệ. JaCoCo áp dụng bộ lọc mã generated tiêu chuẩn của thư viện. Kiểm thử giao diện React được báo riêng bằng Playwright.

| Chỉ số | Kết quả |
|---|---:|
| JUnit/Spring context | 155 test đạt |
| Line coverage của JUnit/context | **43,12%** |
| Line coverage tổng hợp JUnit + API + browser E2E | **71,90%** — 4889/6800 dòng |
| Instruction coverage tổng hợp | 74,42% |
| Branch coverage tổng hợp | 60,28% |
| Class coverage tổng hợp | 232/267 lớp có thực thi |
| Quality gate | **LINE ≥ 71%** trên toàn backend |
| Kiểm tra API trong job integration | 156 kiểm tra đạt |
| Kiểm tra trình duyệt | 31 kiểm tra đạt |

Ngưỡng 71% đáp ứng mục tiêu **lớn hơn 70%**. Tỷ lệ 71,90% là tổng hợp các tầng kiểm thử; không trình bày nó như tỷ lệ riêng của unit test. Mỗi tầng có báo cáo/dữ liệu thực thi riêng để đối chiếu.

**Đã kiểm tra trường hợp thất bại:** tăng ngưỡng tạm thời lên 99% khiến Maven trả mã lỗi; thiếu file thực thi khiến verifier trả lỗi; Chromium không khởi động được khiến runner E2E trả mã lỗi. Cấu hình chính vẫn giữ ngưỡng 71%. Không có `continue-on-error` để bỏ qua lỗi kiểm thử.

## Mở báo cáo

- Tổng hợp: `smart-shift-be/target/site/jacoco/index.html`.
- Riêng JUnit/context: `smart-shift-be/target/site/jacoco-unit/index.html`.
- Báo cáo máy đọc: `jacoco.xml`, `jacoco.csv` trong hai thư mục trên.
- Tóm tắt: `qa-automation/results/coverage-summary.json`.

Mở file `index.html` bằng trình duyệt và chọn package/class để xem dòng được thực thi và dòng còn thiếu. Cột **Cov.** đầu tiên của giao diện JaCoCo là instruction coverage; line coverage được tính từ bộ đếm LINE trong XML. Ví dụ: `4889 / 6800 × 100 = 71,90%`.

Các file thực thi, HTML/XML/JSON và ảnh chụp là kết quả sinh ra, được giữ cục bộ/ở runner; không commit hoặc tự upload ra ngoài. Repo lưu cấu hình, lockfile và mã kiểm thử để tái tạo kết quả.

## Quy trình CI

1. Job backend chạy `clean verify`, gồm unit/Spring integration và tạo báo cáo JUnit coverage.
2. Job frontend chạy `npm ci`, lint và production build.
3. Chỉ khi cả hai job đạt, job integration khởi động PostgreSQL QA, tạo DB `coverage_ci` riêng và chạy lại JUnit với profile `coverage-e2e` trong workspace của job.
4. Profile tạo JAR ứng dụng và sao chép đúng phiên bản agent. Image coverage dùng **chính JAR vừa kiểm thử**, nên bytecode của báo cáo và app chạy API/E2E trùng nhau.
5. Chạy API, rồi E2E bằng Chromium thật.
6. Thu dữ liệu agent, gộp với JUnit, xuất report và thực hiện `jacoco:check` ngưỡng 71%. Verifier còn kiểm tra đủ file dữ liệu và cùng tổng số dòng giữa hai report; thiếu dữ liệu không được coi là đạt.
7. Dừng stack QA trong bước cleanup, kể cả khi kiểm thử lỗi.

JUnit được chạy trong workspace integration để giữ đủ bytecode/dữ liệu coverage trong cùng job; không truyền artifact giữa các job. CI dùng DB/volume riêng của runner, không kết nối DB triển khai. Job có timeout và test lỗi làm toàn workflow thất bại.

Đã chạy các bước kiểm chứng cục bộ bằng Java 17/Linux, PostgreSQL 17 và Chromium. Workflow đã được actionlint kiểm tra; lần chạy thật trên GitHub chỉ có sau khi commit/push.

## Bộ API và trình duyệt

| Bộ API | Số kiểm tra |
|---|---:|
| OpenAPI và hợp đồng HTTP | 22 |
| Monitoring chung | 13 |
| Điều chỉnh công và bảng lương | 30 |
| Ngân sách tuần | 24 |
| Ca gãy/Clopening/đổi ca | 18 |
| Xuất Excel | 24 |
| Lịch kéo thả | 25 |

| Bộ browser | Số kiểm tra | Hành vi chính |
|---|---:|---|
| `app_browser.cjs` | 11 | Chuyển về login, sai mật khẩu, quay về trang đã chọn, dashboard quản lý, ghi nhớ phiên, logout, chặn route quản lý với Employee, lịch cá nhân |
| `calendar_browser.cjs` | 11 | Phân công/chuyển người, ca đầy và rollback, chọn bằng click, tháng/tuần, mobile, lịch công bố chỉ xem |
| `swagger_browser.cjs` | 9 | Try it out, Authorize, JWT, tạo dữ liệu QA, reload xóa JWT, UI hẹp, không lỗi JS/validator |

Playwright được cố định phiên bản **1.64.1** cùng lockfile ở `qa-automation/browser`. Runner dừng và trả lỗi ngay khi một suite thất bại. Browser/calendar fixtures phải được seed mới trước mỗi lượt vì kiểm thử ghi phân công thật vào QA. Drag/drop sử dụng DragEvent/DataTransfer trong Chromium; nghiệm thu cử chỉ chuột/cảm ứng hệ điều hành vẫn là kiểm tra bằng tay.

Kiểm thử UI phát hiện và đã sửa lỗi `PublicOnlyRoute` đẩy người dùng về trang chủ sau đăng nhập, làm mất trang được yêu cầu ban đầu. Cả route công khai và form đăng nhập hiện dùng cùng trang đích được lưu từ route bảo vệ.

## Chạy browser trên máy cá nhân

```powershell
python qa-automation/scripts/calendar_e2e.py
cd qa-automation/browser
npm ci
npx --no-install playwright install chromium
npm test
```

Có thể đặt `CHROME_PATH` để dùng Chromium đã cài; `PLAYWRIGHT_MODULE` được runner truyền tự động cho các suite. Trên Linux CI dùng `playwright install --with-deps chromium` để cài cả thư viện hệ điều hành. Các suite dùng QA tại `http://127.0.0.1:23000`, không dùng stack chính.

## Tái tạo coverage tổng hợp trên Linux

Cần Java 17, Python 3.12, Node 22, Docker Compose. Chạy từ thư mục gốc repo. Các lệnh này chỉ dùng stack QA; database `coverage_ci` tạo một lần, không xóa DB hoặc volume đã có.

```bash
docker compose -f compose.attendance-test.yml -f compose.coverage.yml up -d --wait postgres
docker exec smart-shift-attendance-db psql -U attendance_qa -d postgres -v ON_ERROR_STOP=1 -c 'CREATE DATABASE coverage_ci'

export SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:25432/coverage_ci
export SPRING_DATASOURCE_USERNAME=attendance_qa
export SPRING_DATASOURCE_PASSWORD=attendance_local_only
export ATTENDANCE_ABSENCE_RECONCILIATION_ENABLED=false
export LARK_ENABLED=false
export LARK_APP_ENABLED=false

cd smart-shift-be
./mvnw -Pcoverage-e2e -B --no-transfer-progress clean verify
cd ..
docker compose -f compose.attendance-test.yml -f compose.coverage.yml up -d --build --wait --wait-timeout 180

python -m pip install -r qa-automation/requirements-openapi.txt
python qa-automation/scripts/openapi_sync.py
python qa-automation/scripts/openapi_e2e.py
python qa-automation/scripts/monitoring_e2e.py
python qa-automation/scripts/attendance_workflow_e2e.py
python qa-automation/scripts/budget_e2e.py
python qa-automation/scripts/split_shift_e2e.py
python qa-automation/scripts/excel_e2e.py
python qa-automation/scripts/calendar_e2e.py

cd qa-automation/browser
npm ci
npx --no-install playwright install --with-deps chromium
npm test
cd ../../smart-shift-be
./mvnw -Pcoverage-e2e -B --no-transfer-progress jacoco:dump@dump-e2e jacoco:merge@merge-coverage jacoco:report@combined-report jacoco:check@coverage-threshold
python ../qa-automation/scripts/coverage_report.py
```

Nếu DB `coverage_ci` đã tồn tại, bỏ lệnh tạo DB và tái sử dụng; không cần reset dữ liệu. Trên Windows, phần Java/OR-Tools đã được chạy bằng container Linux từ `qa-automation/ci/Dockerfile`; browser có thể chạy trên host. Profile `coverage-e2e` chỉ chuẩn bị agent/JAR ở bước `verify`; gate tổng hợp được gọi sau API/browser khi đã có dữ liệu E2E.

Overlay `compose.coverage.yml` xuất DB ở `127.0.0.1:25432`, agent ở `127.0.0.1:26300`; cổng agent không có xác thực và chỉ dùng cho QA. Image production không gắn agent. Kết thúc bằng `down` với cùng danh sách compose, giữ volume; dùng lại compose QA thường để trở về image bình thường.

Tham khảo: [JaCoCo Maven](https://www.jacoco.org/jacoco/trunk/doc/maven.html), [quality check](https://www.jacoco.org/jacoco/trunk/doc/check-mojo.html), [Playwright CI](https://playwright.dev/docs/ci).
