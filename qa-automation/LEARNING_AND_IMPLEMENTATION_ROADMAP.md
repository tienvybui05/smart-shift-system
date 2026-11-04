# LỘ TRÌNH HỌC VÀ XÂY DỰNG HỆ THỐNG KIỂM THỬ SMART SHIFT

> Vai trò định hướng: Junior QA / Junior Automation Tester  
> Dự án áp dụng: Smart Shift System  
> Thời gian dự kiến: 5 tuần, 2–3 giờ/ngày, 6 ngày/tuần  
> Thời gian dự kiến: 16/09/2026–21/10/2026

---

## 1. Mục tiêu

### 1.1. Mục tiêu học thuật

- Trình bày được cơ sở lý thuyết kiểm thử phần mềm trong báo cáo đồ án.
- Áp dụng ISO/IEC 9126, ISO/IEC 25010 và kỹ thuật thiết kế test theo ISTQB.
- Xây dựng Test Strategy, Test Plan, RTM, test case và bug report.
- Có bằng chứng kiểm thử để đưa vào báo cáo và slide bảo vệ.
- Giải thích được vì sao chọn từng kỹ thuật cho từng nghiệp vụ.

### 1.2. Mục tiêu nghề nghiệp

- Biết phân tích yêu cầu và thiết kế test thay vì chỉ chạy script có sẵn.
- Kiểm thử REST API bằng Postman và Newman.
- Đối soát PostgreSQL bằng SQL.
- Xây dựng Playwright framework bằng TypeScript và Page Object Model.
- Thiết lập report, trace, screenshot, video và GitHub Actions.
- Đóng gói repository thành portfolio ứng tuyển Junior QA/Automation Tester.

### 1.3. Kết quả cuối cùng

- Test Strategy và Test Plan.
- Requirement Traceability Matrix và RBAC Matrix.
- 80–120 manual test case.
- Ví dụ EP, BVA, Decision Table, State Transition, Use Case và Error Guessing.
- Postman Collection, Environment, JSON Schema và Newman report.
- SQL verification scripts.
- 25–40 Playwright automation test quan trọng.
- Smoke, regression, security và E2E suites.
- Playwright HTML Report, trace, screenshot và video.
- GitHub Actions workflow.
- Jira board để quản lý requirement, task, bug và tiến độ kiểm thử.
- README portfolio và bộ câu hỏi phỏng vấn.

---

## 2. Nguyên tắc học và thực hành

```text
Yêu cầu → Rủi ro → Kỹ thuật thiết kế test → Test case
→ Test data → Automation → Kết quả → Evidence → Defect/Kết luận
```

Trước khi tự động hóa phải trả lời được:

1. Test đang kiểm tra yêu cầu nào?
2. Rủi ro cần phòng ngừa là gì?
3. Expected result lấy từ đâu?
4. Kỹ thuật ISTQB nào được sử dụng?
5. Test nên nằm ở API, UI, database hay nhiều tầng?
6. Dữ liệu trước và sau test thay đổi thế nào?
7. Test có thể chạy lặp lại và độc lập hay không?
8. Khi fail sẽ dùng evidence nào để debug?

Tỷ lệ học khuyến nghị: 30% lý thuyết, 50% thực hành, 20% tài liệu và luyện giải thích.

---

## 3. Phạm vi Smart Shift

### 3.1. Công nghệ

| Thành phần | Công nghệ |
|---|---|
| Backend | Java 17, Spring Boot, Spring Security, JWT, JPA, Flyway |
| Frontend | React, Vite, Ant Design, Axios, React Router |
| Database | PostgreSQL |
| Realtime/Tích hợp | SSE, Lark Webhook và Lark Open API |
| Hạ tầng | Docker, Docker Compose, Nginx |
| Automation | Postman, Newman, Playwright, TypeScript |
| Test/Defect Management | Jira; Xray hoặc Zephyr là phần mở rộng tùy chọn |
| CI/CD | GitHub Actions |

### 3.2. Vai trò

| Vai trò | Phạm vi |
|---|---|
| Admin | Toàn hệ thống, người dùng, danh mục và tích hợp |
| Manager | Nghiệp vụ trong chi nhánh được giao |
| Employee | Lịch, yêu cầu và dữ liệu cá nhân |

### 3.3. Module

1. Authentication và Authorization.
2. Role, Location, Position và User.
3. Shift Template, Schedule Period và Work Shift.
4. Availability và Time Off.
5. Requirement, Assignment và Auto Schedule.
6. Open Shift Claim và Shift Swap.
7. Attendance GPS và Payroll.
8. Notification, SSE, Lark, Audit và Dashboard.

### 3.4. Luồng E2E

```text
Admin tạo nhân viên và mẫu ca
→ Employee đăng ký lịch rảnh/xin nghỉ
→ Manager tạo kỳ lịch, ca và nhu cầu nhân sự
→ Hệ thống tự động xếp lịch
→ Manager kiểm tra, điều chỉnh và công bố
→ Employee xem lịch, nhận ca hoặc đổi ca
→ Employee check-in/check-out bằng GPS
→ Manager duyệt chấm công
→ Hệ thống tính công và lương dự tính
→ Manager xác nhận bảng lương
```

---

## 4. Phần 1 — Nền tảng QA và tiêu chuẩn chất lượng

### 4.1. Khái niệm phải học

- QA/QC; Testing/Debugging.
- Error, Defect, Failure và Root Cause.
- Verification và Validation.
- Static/Dynamic; Positive/Negative Testing.
- Functional/Non-functional Testing.
- Black-box, White-box và Experience-based Testing.
- Test Basis, Condition, Scenario, Case và Suite.
- Test Oracle, Test Data và Test Environment.
- Entry/Exit Criteria và Risk-based Testing.
- Smoke, Sanity, Regression và Re-testing.
- Test Coverage và Traceability.

### 4.2. SDLC và STLC

```text
SDLC: Requirement → Design → Implementation → Testing → Deployment → Maintenance
STLC: Requirement Analysis → Test Planning → Test Design
      → Environment Setup → Execution → Defect Reporting → Closure
```

Thực hành:

- Ánh xạ từng giai đoạn STLC vào Smart Shift.
- Xác định đầu vào, đầu ra và trách nhiệm QA ở từng giai đoạn.
- Giải thích hậu quả nếu chỉ kiểm thử ở cuối dự án.

### 4.3. Test level và test type

| Test level | Áp dụng |
|---|---|
| Unit Test | Service và business rule backend |
| Integration Test | Controller–Service–Repository–Database |
| System Test | Backend, frontend và PostgreSQL |
| Acceptance Test | Luồng demo theo yêu cầu đồ án |

Test type: Functional, API, UI, Database, Security, Usability, Compatibility, Performance check, Reliability và Recovery.

### 4.4. ISO/IEC 9126 và ISO/IEC 25010

ISO/IEC 9126 là mô hình chất lượng trước đây; ISO/IEC 25010 là mô hình kế thừa và được dùng làm khung đánh giá chính.

| Đặc tính | Áp dụng vào Smart Shift |
|---|---|
| Functional suitability | Yêu cầu chức năng và tính đúng nghiệp vụ |
| Performance efficiency | Response time và xử lý dữ liệu |
| Compatibility | REST API, database, SSE và Lark |
| Usability | Form, thông báo lỗi và trải nghiệm |
| Reliability | Phục hồi khi request/tích hợp thất bại |
| Security | JWT, RBAC, IDOR và injection |
| Maintainability | POM, tái sử dụng và mở rộng test |
| Portability | Docker và đa trình duyệt |

Trọng tâm báo cáo:

- **Suitability:** Use Case Testing và RTM.
- **Interoperability:** frontend–API–database–SSE–Lark.
- **Security:** JWT, RBAC, location scope, IDOR, SQL Injection và XSS.
- **Accuracy:** giờ làm, nghỉ, GPS, đi trễ, về sớm và payroll.

### 4.5. Test Strategy, Test Plan, RTM và Bug Life Cycle

Test Strategy phải có mục tiêu, phạm vi, test level/type, kỹ thuật, công cụ, environment, test data, automation, risk, entry/exit criteria, defect management và reporting.

RTM tối thiểu:

| Requirement ID | Module | Requirement | Risk | Test Case ID | Test Type | Automation ID | Status |
|---|---|---|---|---|---|---|---|

Bug Life Cycle:

```text
New → Assigned → In Progress → Fixed → Retest → Closed
                                    ↘ Reopened
```

Bug Report gồm title, module, environment, preconditions, steps, actual/expected result, severity, priority và evidence.

### 4.6. Jira cho quản lý kiểm thử và lỗi

Mục tiêu sử dụng Jira:

- Quản lý backlog công việc kiểm thử.
- Theo dõi requirement, task, test execution và defect.
- Liên kết bug với requirement, test case và automation test liên quan.
- Theo dõi trạng thái sửa lỗi và retest.
- Tạo dashboard phục vụ báo cáo tiến độ.

Các khái niệm Jira phải học:

- Project, board, backlog và sprint.
- Epic, Story, Task, Sub-task và Bug.
- Assignee, Reporter, Watcher và Component.
- Priority, label, fix version và attachment.
- Workflow, transition, resolution và comment.
- Link issue: blocks, is blocked by, relates to và duplicates.
- Filter và JQL cơ bản.
- Dashboard, chart và report.

Workflow Bug đề xuất:

```text
Open → Assigned → In Progress → Fixed → Ready for Retest
→ Retest Passed → Closed
→ Retest Failed → Reopened → In Progress
```

Resolution bổ sung:

- Fixed.
- Duplicate.
- Cannot Reproduce.
- Won't Fix.
- Not a Bug.
- Deferred.

Field của Jira Bug:

| Field | Nội dung |
|---|---|
| Summary | Mô tả lỗi ngắn gọn, rõ hành vi |
| Description | Bối cảnh và mô tả chi tiết |
| Environment | Browser, OS, build, API và database |
| Preconditions | Trạng thái dữ liệu/tài khoản trước test |
| Steps to Reproduce | Các bước tái hiện đánh số |
| Actual Result | Hành vi thực tế |
| Expected Result | Hành vi đúng theo requirement |
| Severity | Blocker/Critical/Major/Minor/Trivial |
| Priority | Highest/High/Medium/Low/Lowest |
| Component | Module Smart Shift liên quan |
| Labels | API, UI, Database, Security, Regression... |
| Attachment | Screenshot, video, trace, log, request/response |
| Linked Issues | Requirement, story hoặc task liên quan |

Quy ước Summary:

```text
[Module][Layer] Hành vi lỗi khi điều kiện xảy ra
```

Ví dụ:

```text
[Attendance][API] Employee vẫn check-in thành công khi GPS nằm ngoài bán kính
[RBAC][API] Manager truy cập được dữ liệu của chi nhánh khác
[Payroll][UI] Tổng lương không cập nhật sau khi thay đổi tiền thưởng
```

JQL cơ bản cần thực hành:

```text
project = SMARTSHIFT AND issuetype = Bug
project = SMARTSHIFT AND status = Reopened
project = SMARTSHIFT AND priority in (Highest, High)
project = SMARTSHIFT AND labels = regression
project = SMARTSHIFT AND component = Attendance
```

Chiến lược quản lý test case:

- Giai đoạn đầu: lưu test case chi tiết trong Excel/Google Sheets và dùng Jira quản lý task/bug/execution summary.
- Liên kết Test Case ID và Requirement ID vào Jira issue.
- Nếu cần test management chuyên sâu, nghiên cứu Xray hoặc Zephyr sau khi quy trình cơ bản đã ổn định.
- Không phụ thuộc plugin Jira ngay từ đầu vì mục tiêu chính là hiểu quy trình QA.

Thực hành Jira bắt buộc:

1. Tạo project hoặc board Smart Shift QA.
2. Tạo Epic cho từng nhóm module.
3. Tạo Story/Task cho hoạt động thiết kế và automation test.
4. Tạo ít nhất ba Bug mẫu từ API, UI và business rule.
5. Chuyển bug qua workflow đến Retest/Closed hoặc Reopened.
6. Link bug với requirement và test case.
7. Tạo filter theo severity, module và trạng thái.
8. Tạo dashboard hiển thị bug theo status, priority và component.

### 4.7. Đầu ra và bằng chứng báo cáo

- `TEST_STRATEGY.md`, `TEST_PLAN.md`, RTM và RBAC Matrix.
- Risk list và Bug Report Template.
- Jira backlog, bug workflow, filters và dashboard.
- Chụp sơ đồ STLC, bảng ISO/IEC 25010, RTM và Bug Life Cycle.
- Chụp một Jira Bug hoàn chỉnh, board và dashboard để đưa vào báo cáo/portfolio.

---

## 5. Phần 2 — Kỹ thuật thiết kế test theo ISTQB

### 5.1. Equivalence Partitioning — EP

Phải học:

- Chia miền dữ liệu thành các phân vùng có hành vi tương đương.
- Có phân vùng hợp lệ và không hợp lệ.
- Mỗi phân vùng chọn ít nhất một giá trị đại diện.
- Áp dụng cho số, chuỗi, enum, ngày giờ và điều kiện nghiệp vụ.

Ví dụ latitude:

| Partition | Điều kiện | Hợp lệ |
|---|---|---|
| EP-LAT-01 | Latitude < -90 | Không |
| EP-LAT-02 | -90 ≤ Latitude ≤ 90 | Có |
| EP-LAT-03 | Latitude > 90 | Không |
| EP-LAT-04 | Null | Không |
| EP-LAT-05 | Sai kiểu dữ liệu | Không |

Form cần áp dụng EP:

- Login và User.
- Location và Shift Template.
- Schedule Period và Work Shift.
- Availability và Time Off.
- Open Shift Claim và Shift Swap.
- Attendance GPS và Payroll bonus.
- Lark test message.

### 5.2. Boundary Value Analysis — BVA

Phải học:

- Lỗi thường xuất hiện tại biên hơn ở giữa miền dữ liệu.
- BVA hai điểm và BVA ba điểm.
- Bộ giá trị `min-1`, `min`, `min+1`, `max-1`, `max`, `max+1`.
- Kết hợp EP để tránh test trùng lặp.

Ví dụ latitude:

| Test ID | Giá trị | Expected result |
|---|---:|---|
| BVA-LAT-01 | -90.01 | Từ chối |
| BVA-LAT-02 | -90 | Chấp nhận |
| BVA-LAT-03 | -89.99 | Chấp nhận |
| BVA-LAT-04 | 89.99 | Chấp nhận |
| BVA-LAT-05 | 90 | Chấp nhận |
| BVA-LAT-06 | 90.01 | Từ chối |

Biên quan trọng:

- Latitude -90/90; Longitude -180/180.
- Bán kính chấm công 10/5000 mét.
- Break minutes và duration ca.
- Độ dài username, password, name và note.
- Giờ làm ngày/tuần, min rest và max consecutive days.
- Base pay, salary coefficient và bonus.
- Ngày đầu/cuối kỳ lịch.
- Thời điểm trước/tại/sau giờ bắt đầu ca.
- Khoảng ngày truy vấn attendance tối đa.

### 5.3. Decision Table Testing

Phải học condition, action, rule, tổ hợp điều kiện và cách rút gọn bảng.

Decision Table bắt buộc:

1. **RBAC:** token, role, ownership và location scope.
2. **Assignment Eligibility:** active, role, location, position, availability, time off, overlap, rest, daily/weekly hours.
3. **Publish Schedule:** period state, shift, requirement, min/max staffing, constraint và thời điểm ca.
4. **Time Off Approval:** state, location, thời gian và assignment xung đột.
5. **Open Shift Claim:** employee, shortage, period, duplicate claim và constraint lúc duyệt.
6. **Shift Swap:** ownership, thời điểm, period state, người nhận, constraint và manager approval.

Decision Tree hỗ trợ Auto Schedule:

```text
Employee active?
├── No → Ineligible
└── Yes
    └── Employee role?
        ├── No → Ineligible
        └── Yes
            └── Same location and position?
                ├── No → Ineligible
                └── Yes
                    └── Availability covers shift?
                        ├── No → Ineligible
                        └── Yes
                            └── Time off/overlap/rest/hour violation?
                                ├── Yes → Ineligible
                                └── No → Eligible candidate
```

### 5.4. State Transition Testing

Phải học state, event, transition, guard condition, action và invalid transition.

Các state model bắt buộc:

```text
Schedule Period: DRAFT → GENERATING → DRAFT → PUBLISHED → LOCKED
Work Shift:      OPEN → FILLED → COMPLETED
                 OPEN/FILLED → CANCELLED
Time Off:        PENDING → APPROVED/REJECTED/CANCELLED
Open Shift:      PENDING → APPROVED/REJECTED/CANCELLED
Shift Swap:      PENDING → ACCEPTED → APPROVED
                 PENDING → DECLINED/CANCELLED/REJECTED
Payroll:         DRAFT → CONFIRMED
User:            ACTIVE ↔ INACTIVE
```

Test bắt buộc:

- Transition hợp lệ và không hợp lệ.
- Generate khi period không phải DRAFT.
- Publish khi staffing hoặc constraint không hợp lệ.
- Lock khi chưa publish.
- Chỉnh sửa sau publish/lock.
- Generate lỗi không làm period kẹt GENERATING.
- User tự khóa chính mình.
- JWT cũ của user bị khóa.

### 5.5. Use Case Testing

Phải học actor, goal, precondition, trigger, main flow, alternative flow, exception flow và postcondition.

Use Case bắt buộc:

1. Đăng nhập theo ba role.
2. Admin tạo Employee đến Employee xem lịch.
3. Time off được duyệt và ảnh hưởng Auto Schedule.
4. Nhận ca trống và Manager duyệt.
5. Đổi/nhường ca và Manager duyệt.
6. Chấm công đến tính và xác nhận lương.
7. Manager bị giới hạn theo location.

### 5.6. Error Guessing

Checklist bắt buộc:

- Double-click Submit hoặc Approve.
- Gửi request lặp.
- Refresh khi modal đang mở.
- Mất mạng sau khi Save.
- Token hết hạn giữa thao tác.
- JWT của user vừa bị khóa.
- Thay resource ID, user ID hoặc location ID.
- Số âm, 0, rất lớn, null, blank và sai kiểu.
- Unicode, HTML/JavaScript và chuỗi SQL trong input.
- Hai Manager duyệt cùng request.
- Hai Employee cùng nhận một ca.
- Ca qua ngày 22:00–06:00 hoặc tại biên ngày/tuần/tháng.
- Check-out trước check-in.
- GPS đúng khoảng cách nhưng accuracy kém và ngược lại.
- Dữ liệu bị thay đổi giữa lúc mở form và submit.
- Lark lỗi nhưng nghiệp vụ chính vẫn commit.
- SSE mất kết nối và kết nối lại.

### 5.7. Template test case

| Trường | Nội dung |
|---|---|
| Test Case ID | Mã duy nhất |
| Requirement ID | Yêu cầu được kiểm tra |
| Module/Title | Module và mô tả ngắn |
| Technique | EP/BVA/Decision Table/State/Use Case/Error Guessing |
| Priority | High/Medium/Low |
| Preconditions | Điều kiện trước test |
| Test Data | Dữ liệu kiểm thử |
| Steps | Các bước thực hiện |
| Expected/Actual | Kết quả mong đợi/thực tế |
| Status | Pass/Fail/Blocked/Not Run |
| Automation | Manual/Automated/Planned |
| Evidence | Screenshot, response, query hoặc report |

### 5.8. Đầu ra và bằng chứng báo cáo

- Bảng EP/BVA cho các form chính.
- Decision Tables cho RBAC, Assignment, Publish, Time Off, Claim và Swap.
- Decision Tree Auto Schedule.
- State Transition Diagrams.
- 7 Use Case Specifications và Error Guessing Checklist.
- 80–120 manual test case.
- Chụp ít nhất một ví dụ hoàn chỉnh của mỗi kỹ thuật.

---

## 6. Phần 3 — API Testing bằng Postman và Newman

### 6.1. HTTP/REST phải học

- Request/response; URL, path/query parameter, header và body.
- GET, POST, PUT, PATCH, DELETE.
- JSON object/array, Content-Type và Authorization.
- Stateless API và idempotency.

Status code trọng tâm:

| Code | Ý nghĩa |
|---:|---|
| 200/201/204 | Thành công |
| 400 | Validation/business rule không hợp lệ |
| 401 | Chưa xác thực hoặc token không hợp lệ |
| 403 | Đã xác thực nhưng không có quyền |
| 404 | Không tìm thấy resource |
| 409 | Duplicate/conflict |
| 500 | Lỗi hệ thống ngoài dự kiến |

### 6.2. Cấu trúc Collection

```text
Smart Shift API
├── Authentication
├── References and Users
├── Shift Templates
├── Availability and Time Off
├── Periods, Work Shifts, Requirements and Assignments
├── Auto Schedule
├── Open Shift Claims and Shift Swaps
├── Attendance and Payroll
├── Notifications, Lark and Audit
├── Security and RBAC
└── End-to-End Workflows
```

### 6.3. Environment và JWT

Biến cần có: `baseUrl`, tài khoản/token ba role, các ID của user, location, position, period, shift, assignment, request, claim, swap, attendance và payroll.

Phải biết:

- Login và lưu JWT vào environment.
- Gán `Authorization: Bearer {{token}}`.
- Đổi token theo role.
- Không commit secret thật.
- Kiểm tra expiration JWT ở mức cơ bản.

### 6.4. Assertions và JSON Schema

- Status code, response time và Content-Type.
- Field, kiểu dữ liệu, array và error structure.
- Dữ liệu trả về đúng request, role, owner và location.
- JSON Schema: `type`, `properties`, `required`, `items`, `enum`, `format`, `minimum`, `maximum`.

### 6.5. Bộ case cho mỗi endpoint

- Happy path.
- Missing/null/blank/wrong type/invalid enum.
- EP representative và BVA values.
- Not found, duplicate và invalid state.
- No token, invalid token và wrong role.
- Other location/owner.
- Repeated request.

### 6.6. Newman và API E2E

Phải biết chạy collection/folder, environment, data-driven JSON/CSV, CLI/JUnit/HTML report và làm pipeline fail khi assertion fail.

Workflow cần tự động hóa:

1. Admin tạo Employee → Employee login.
2. Availability → period/shift/requirement → Auto Schedule.
3. Publish → Employee xem lịch.
4. Claim → Manager approve.
5. Swap → respond → Manager approve.
6. Check-in/out → approve attendance → payroll → confirm.

### 6.7. Đầu ra và bằng chứng báo cáo

- Collection, Environments và JWT scripts.
- Common assertions và JSON Schemas.
- Security/RBAC folder và E2E folder.
- Newman command/report.
- Chụp collection tree, environment đã che secret, JWT script, assertions và report.

---

## 7. Phần 4 — Database Testing

### 7.1. SQL phải học

- SELECT, WHERE, ORDER BY và LIMIT.
- INNER JOIN, LEFT JOIN.
- GROUP BY, HAVING, COUNT, SUM, AVG, MIN, MAX.
- CASE WHEN, subquery và CTE cơ bản.
- PK, FK, unique, check, index, nullability.
- Transaction, timestamp và timezone.

### 7.2. Quy trình đối soát

```text
Query dữ liệu trước → chạy API/UI action → query dữ liệu sau
→ so sánh expected/actual → kiểm tra không có side effect ngoài dự kiến
```

Phải kiểm tra rollback khi request thất bại và không sửa dữ liệu thủ công để làm test pass.

### 7.3. Kiểm tra theo module

- **User:** uniqueness, password hash, role/location/position và không xóa lịch sử khi khóa.
- **Schedule:** period overlap, shift duplicate, requirement/assignment uniqueness và relation đúng.
- **Availability/Time Off:** timezone, overlap, reviewer và timestamp.
- **Claim/Swap:** assignment được tạo/cập nhật đúng một lần và transaction không dở dang.
- **Attendance:** một attendance/assignment, GPS, late/early/overtime và approval.
- **Payroll:** tổng giờ, bonus, total pay và trạng thái confirmed.
- **Notification/Audit/Lark:** đúng recipient, read state, before/after và integration failure isolation.

### 7.4. SQL suite dự kiến

```text
database/
├── user_verification.sql
├── schedule_verification.sql
├── assignment_verification.sql
├── time_off_verification.sql
├── claim_swap_verification.sql
├── attendance_verification.sql
├── payroll_verification.sql
├── notification_audit_verification.sql
└── cleanup_test_data.sql
```

### 7.5. Đầu ra và bằng chứng báo cáo

- Data dictionary và SQL verification scripts.
- Query phát hiện duplicate/orphan và cleanup an toàn.
- Bảng đối soát API result với database result.
- Chụp ERD, JOIN query và dữ liệu trước/sau.

---

## 8. Phần 5 — UI Automation bằng Playwright và TypeScript

### 8.1. TypeScript đủ dùng

- `const`, `let`, primitive, array và object.
- Function, async/await và Promise.
- Interface, type alias và optional property.
- Class, constructor, import/export và try/catch.

Không cần học toàn bộ TypeScript trước khi viết Playwright.

### 8.2. Playwright fundamentals

- Test, describe và hooks.
- Browser, context và page.
- Locator, assertion và auto-waiting.
- Navigation, form, modal, table, filter và pagination.
- Authentication state, fixtures, projects và tags.
- Retry, timeout và parallel execution.

### 8.3. Locator strategy

Ưu tiên:

1. `getByRole`.
2. `getByLabel`.
3. `getByPlaceholder`.
4. `getByText` khi nội dung ổn định.
5. `data-testid` khi cần.
6. CSS selector chỉ khi không có lựa chọn tốt hơn.

Không dùng XPath dài, class sinh tự động hoặc `waitForTimeout()` để che test flaky.

### 8.4. Page Object Model

- `pages/` chứa locator và hành động.
- `tests/` chứa test case và assertion nghiệp vụ.
- Page Object không hardcode test data.
- Không nhét mọi assertion vào Page Object.
- Test không truy cập locator nội bộ khi đã có action phù hợp.

Cấu trúc dự kiến:

```text
qa-automation/
├── docs/
├── manual-tests/
├── postman/
├── database/
├── playwright/
│   ├── pages/
│   ├── fixtures/
│   ├── test-data/
│   ├── utils/
│   └── tests/
│       ├── smoke/
│       ├── regression/
│       ├── security/
│       └── e2e/
├── playwright.config.ts
├── package.json
└── README.md
```

Page Object dự kiến: Login, Dashboard, User, Availability, Time Off, Schedule Period, Work Shift, Open Shift, Shift Swap, Attendance và Payroll.

### 8.5. Fixtures và authentication

Phải học built-in/custom fixture, test/worker scope, login fixture, API setup và cleanup.

Fixtures dự kiến:

- `adminPage`, `managerPage`, `employeePage`.
- `apiClient`, `testUsers`, `scheduleData`.

Authentication strategy:

- Login bằng UI chỉ cho test chức năng login.
- Các test khác tái sử dụng `storageState` theo từng role.
- Không lưu mật khẩu thật trong source.
- Tạo lại state khi token hết hạn.

### 8.6. Test data strategy

- Dùng prefix riêng cho automation.
- Test độc lập, không phụ thuộc thứ tự.
- Có setup/cleanup và không xóa dữ liệu ngoài phạm vi.
- Không dùng chung user nếu parallel test có thể xung đột.
- Dùng API setup khi thao tác UI không phải mục tiêu test.

### 8.7. Playwright configuration

- `baseURL`, Chromium và Firefox.
- Headless mặc định, retry trên CI.
- Screenshot/video khi fail và trace khi retry/fail.
- HTML/JUnit reporter.
- Timeout hợp lý.
- `webServer` hoặc cơ chế khởi động Docker/app.

### 8.8. UI test bắt buộc

#### Smoke

- Login ba role, Dashboard và navigation.
- Protected route và backend unavailable.

#### Authentication/RBAC

- Sai credential và required fields.
- Menu/route theo role.
- Logout và truy cập lại protected route.

#### User

- Tạo, update, lock/unlock và reset password.
- EP/BVA form; search/filter/pagination.

#### Availability/Time Off

- Tạo/sửa/xóa availability.
- Không sửa dữ liệu quá khứ.
- Gửi/hủy/approve/reject time off.

#### Schedule

- Tạo period, sinh work shift, requirement và manual assignment.
- Auto Schedule, publication check, publish và lock.

#### Claim/Swap

- Claim/cancel/review.
- Tạo swap/giveaway, accept/decline và approve/reject.

#### Attendance/Payroll

- Mock geolocation.
- Check-in/out; outside radius; invalid accuracy.
- Approve attendance; calculate payroll; bonus; confirm.

#### Notification

- Hiển thị notification, unread count, mark read/all read và reconnect cơ bản.

### 8.9. E2E suite

1. Tạo Employee → availability → xếp lịch → xem lịch.
2. Time off được duyệt → không được auto-assign vào ca xung đột.
3. Open shift claim → Manager approve → assignment được tạo.
4. Attendance → approval → payroll calculation → confirmation.

### 8.10. Chống flaky và debug

- Không sleep cố định; chờ theo UI/network state.
- Dùng locator assertion có auto-retry.
- Test data độc lập và health check environment.
- Đọc trace trước khi tăng timeout.
- Biết dùng `--headed`, `--debug`, Inspector, Trace Viewer, HTML Report, screenshot, video, console và network log.

### 8.11. Đầu ra và bằng chứng báo cáo

- Project Playwright TypeScript, POM, fixtures và test data.
- 25–40 test trong smoke/regression/security/E2E suites.
- Chromium/Firefox và HTML Report.
- Chụp framework architecture, POM, test code, report và trace.

---

## 9. Phần 6 — Security và Non-functional Testing

### 9.1. Security cơ bản

Chỉ thực hiện trên môi trường được phép.

Authentication:

- Không token, token sai định dạng/chữ ký/hết hạn.
- Token của user bị khóa.
- Token sai role.

Authorization:

- Employee gọi API Admin.
- Manager truy cập location khác.
- User truy cập resource của người khác.
- Thay ID trên URL hoặc payload.

Input validation:

- SQL Injection/XSS cơ bản.
- Chuỗi quá dài, invalid JSON/enum và số rất lớn.
- Path/query parameter sai kiểu.

Sensitive data:

- Không trả password hash.
- Log không chứa password hoặc JWT đầy đủ.
- Không commit secret hoặc `.env` thật.

### 9.2. Performance check

- Đặt response-time threshold cho login, search, schedule generation và dashboard.
- Chạy lặp để phát hiện bất thường.
- Phân biệt Postman response-time check với load test thực sự.
- Nếu mở rộng, dùng k6 hoặc JMeter ở mức cơ bản.

Không tuyên bố hiệu năng cao chỉ dựa trên một request.

### 9.3. Reliability, recovery và compatibility

- Backend/database/Lark unavailable.
- SSE mất kết nối.
- Network timeout và request retry.
- Submit lặp và transaction rollback.
- Generate thất bại không để state dở dang.
- Chromium, Firefox, desktop/mobile viewport cơ bản.
- Docker, timezone và ca qua ngày.

### 9.4. Usability checklist

- Label và required indicator rõ ràng.
- Error gần field và không mất dữ liệu không cần thiết.
- Button disabled/loading khi submit.
- Empty state và confirmation cho action nguy hiểm.
- Date/time đúng timezone và màu trạng thái nhất quán.
- Keyboard navigation cơ bản.

### 9.5. Đầu ra và bằng chứng báo cáo

- Security checklist và RBAC/API security suite.
- Injection cases, response-time report và compatibility matrix.
- Usability/recovery checklist.
- Chụp ví dụ IDOR bị chặn, validation injection, bảng response time và compatibility matrix.

---

## 10. Phần 7 — CI/CD, báo cáo và portfolio

### 10.1. Git cần học

- Working tree, staging, commit, branch và pull request.
- Merge conflict cơ bản và `.gitignore`.
- Commit message rõ nghĩa.
- Không commit secret, report nặng hoặc artifact tạm.

### 10.2. GitHub Actions

Workflow dự kiến:

1. Checkout source.
2. Cài Node.js và dependencies.
3. Cài Playwright browsers.
4. Khởi động Smart Shift/test environment.
5. Health check.
6. Chạy Newman smoke test.
7. Chạy Playwright smoke/regression.
8. Upload report, trace, screenshot và JUnit result.

Phải học trigger `push`, `pull_request`, `workflow_dispatch`, job, step, secret, cache, artifact và exit code.

### 10.3. Test Summary

- Build/version và environment.
- Execution time.
- Tổng test, Pass/Fail/Skipped/Blocked.
- Requirement coverage.
- Defect theo severity.
- Blocker và risk còn lại.
- Release recommendation.

Không chỉ chụp test xanh; phải giải thích coverage và giới hạn.

### 10.4. Portfolio README

README phải có:

1. Project overview và system under test.
2. Testing objectives và architecture.
3. Technology stack và folder structure.
4. Test design techniques.
5. Installation và environment configuration.
6. Lệnh chạy API/UI/smoke/regression.
7. Reporting và CI/CD.
8. Coverage, known limitations và lessons learned.
9. Screenshots.

Lệnh mục tiêu:

```bash
npm ci
npm run test:api
npm run test:ui
npm run test:smoke
npm run test:regression
npm run test:headed
npm run report
```

### 10.5. Chuẩn bị phỏng vấn

Manual Testing:

- EP khác BVA thế nào?
- Khi nào dùng Decision Table/State Transition?
- Severity khác Priority thế nào?
- Regression khác Re-testing thế nào?
- RTM dùng để làm gì?

API/SQL:

- 401 khác 403; PUT khác PATCH; idempotency là gì?
- Lưu JWT và kiểm tra JSON Schema thế nào?
- Kiểm tra RBAC và API–database consistency thế nào?
- INNER JOIN khác LEFT JOIN; kiểm tra duplicate/rollback thế nào?

Playwright:

- POM, fixtures và `storageState` giải quyết vấn đề gì?
- Vì sao không dùng `waitForTimeout()`?
- Cách chọn locator và debug flaky test?
- Trace khác screenshot thế nào?

Smart Shift:

- Module rủi ro cao nhất và lý do.
- Hard constraints của Auto Schedule.
- Kiểm tra Manager location scope, GPS và payroll.
- Kiểm tra concurrency khi nhận ca.
- Lark lỗi có rollback nghiệp vụ chính không?

### 10.6. Đầu ra và bằng chứng báo cáo

- GitHub Actions workflow.
- Test/Defect Summary.
- Portfolio README và ảnh/video demo.
- Interview question set và CV bullet points.
- Chụp CI pipeline, GitHub Actions result và final report.

---

## 11. Ma trận kỹ thuật theo module

| Module | EP/BVA | Decision Table | State Transition | Use Case | Error Guessing | API | DB | UI |
|---|---|---|---|---|---|---|---|---|
| Authentication | Có | Có | Có | Có | Có | Có | Có | Có |
| User | Có | Có | Có | Có | Có | Có | Có | Có |
| Shift Template | Có | Có | Có | Có | Có | Có | Có | Có |
| Availability | Có | Có | Có | Có | Có | Có | Có | Có |
| Time Off | Có | Có | Có | Có | Có | Có | Có | Có |
| Schedule Period | Có | Có | Có | Có | Có | Có | Có | Có |
| Work Shift | Có | Có | Có | Có | Có | Có | Có | Có |
| Requirement/Assignment | Có | Có | Có | Có | Có | Có | Có | Có |
| Auto Schedule | Có | Có | Có | Có | Có | Có | Có | Có |
| Open Shift Claim | Có | Có | Có | Có | Có | Có | Có | Có |
| Shift Swap | Có | Có | Có | Có | Có | Có | Có | Có |
| Attendance | Có | Có | Có | Có | Có | Có | Có | Có |
| Payroll | Có | Có | Có | Có | Có | Có | Có | Có |
| Notification/SSE/Lark | Có | Có | Có | Có | Có | Có | Có | Có |

---

## 12. Lịch học và thực hành 5 tuần

## Tuần 1 — QA Foundation, ISO/IEC và EP/BVA

### Mục tiêu

- Hiểu SDLC, STLC, test level và test type.
- Hiểu ISO/IEC 9126 và ISO/IEC 25010.
- Biết viết Test Strategy, Test Plan và RTM.
- Áp dụng EP/BVA vào form Smart Shift.

| Ngày | Nội dung |
|---|---|
| Ngày 1 | QA/QC, testing, defect, verification/validation, SDLC |
| Ngày 2 | STLC, test level/type, smoke, regression và re-test |
| Ngày 3 | ISO/IEC 25010 và ánh xạ vào Smart Shift |
| Ngày 4 | Test Strategy, Test Plan, risk, RTM và Jira workflow |
| Ngày 5 | EP cho Login, User, Availability và Attendance |
| Ngày 6 | BVA cho các field giới hạn và hoàn thiện test case |

Đầu ra:

- Test Strategy/Test Plan/RTM bản đầu.
- Bảng EP/BVA.
- 20–30 test case.
- Jira board, backlog, bug template và workflow.
- Ảnh bảng ISO/IEC 25010, EP/BVA và RTM cho báo cáo.
- Ảnh Jira board và một Bug mẫu hoàn chỉnh.

## Tuần 2 — Decision Table, State Transition, Use Case và Error Guessing

### Mục tiêu

- Thiết kế bảng quyết định cho nghiệp vụ nhiều điều kiện.
- Kiểm thử vòng đời trạng thái.
- Viết Use Case Test end-to-end.
- Xây dựng Error Guessing Checklist.

| Ngày | Nội dung |
|---|---|
| Ngày 1 | Decision Table cho RBAC |
| Ngày 2 | Decision Table và Decision Tree cho Auto Schedule |
| Ngày 3 | State Transition cho Schedule, Time Off, Claim và Swap |
| Ngày 4 | Use Case Test cho các luồng E2E |
| Ngày 5 | Error Guessing và concurrency scenarios |
| Ngày 6 | Hoàn thiện 80–120 test case và cập nhật RTM |

Đầu ra:

- RBAC Matrix và Decision Tables.
- Auto Schedule Decision Tree.
- State Transition Diagrams.
- Use Case Specifications và Error Guessing Checklist.
- Một ví dụ mỗi kỹ thuật để đưa vào báo cáo.

## Tuần 3 — Postman, Newman và Database Testing

### Mục tiêu

- Kiểm thử API theo module và tự động lấy JWT.
- Viết assertions và JSON Schema.
- Chạy collection bằng Newman.
- Đối soát database bằng SQL.

| Ngày | Nội dung |
|---|---|
| Ngày 1 | HTTP, REST, status code, collection/environment |
| Ngày 2 | Authentication, JWT và RBAC API |
| Ngày 3 | User, reference và schedule APIs |
| Ngày 4 | Claim, swap, attendance và payroll APIs |
| Ngày 5 | JSON Schema, data-driven test và Newman |
| Ngày 6 | SQL verification và API–DB E2E |

Đầu ra:

- Collection, Environments và JWT scripts.
- Assertions, schemas, Newman report và SQL suite.
- Ảnh collection tree, JWT script, report và dữ liệu trước/sau.

## Tuần 4 — Playwright TypeScript và POM

### Mục tiêu

- Xây dựng UI automation framework.
- Tạo Page Object Model và fixtures.
- Chạy đa trình duyệt và xuất report/trace.

| Ngày | Nội dung |
|---|---|
| Ngày 1 | TypeScript đủ dùng và Playwright fundamentals |
| Ngày 2 | Config, locator, assertion và authentication state |
| Ngày 3 | POM, fixtures và test data |
| Ngày 4 | Login/RBAC/User automation |
| Ngày 5 | Schedule/Claim/Swap automation |
| Ngày 6 | Attendance/Payroll/E2E và report |

Đầu ra:

- Playwright project, POM, fixtures và suites.
- HTML Report, trace, screenshot và video.
- Ảnh framework tree, POM, test và report cho báo cáo.

## Tuần 5 — Security, CI/CD, Portfolio và phỏng vấn

### Mục tiêu

- Hoàn thiện security/non-functional checks.
- Chạy automation trên GitHub Actions.
- Hoàn thiện README, report và luyện phỏng vấn.

| Ngày | Nội dung |
|---|---|
| Ngày 1 | JWT, RBAC, IDOR, SQL Injection và XSS |
| Ngày 2 | Response time, compatibility, reliability và usability |
| Ngày 3 | GitHub Actions cho API và UI tests |
| Ngày 4 | Test Summary, defect metrics và report |
| Ngày 5 | README portfolio và ảnh minh họa |
| Ngày 6 | Mock interview và demo E2E |

Đầu ra:

- Security/non-functional suite.
- GitHub Actions và final reports.
- Portfolio README và interview question set.
- Ảnh CI, security matrix và Test Summary cho báo cáo.

---

## 13. Ưu tiên automation

### Priority 1 — Smoke

- Login ba role, JWT/RBAC cơ bản và Dashboard.
- User creation, schedule generation và publish.
- Employee xem lịch.

### Priority 2 — Business critical

- Assignment constraints và Time Off ảnh hưởng scheduling.
- Open Shift Claim và Shift Swap.
- Attendance GPS và Payroll.

### Priority 3 — Regression

- EP/BVA validation.
- Invalid state transitions.
- Cross-location access và duplicate requests.
- Search, filter và pagination.

Không tự động hóa ngay các test chạy một lần, UI thay đổi liên tục/rủi ro thấp, đánh giá chủ quan hoặc phụ thuộc tích hợp ngoài không ổn định.

---

## 14. Entry Criteria và Exit Criteria

### Entry Criteria

- Requirement đủ rõ để xác định expected result.
- Environment khởi động được và có tài khoản test.
- Database có seed/setup data.
- Endpoint/màn hình đã triển khai.
- Không có blocker ngăn toàn bộ luồng.

### Exit Criteria

- Smoke test pass.
- Không còn blocker/critical defect chưa xử lý.
- High defect còn lại đã đánh giá rủi ro.
- Requirement quan trọng có test case và RTM.
- API/UI E2E chính chạy được.
- Report, evidence và known limitations đã lưu.

---

## 15. Definition of Done cho portfolio

- [ ] Có Test Strategy và Test Plan.
- [ ] Có RTM liên kết requirement với manual/automation test.
- [ ] Có Jira board, Bug workflow, filters và dashboard.
- [ ] Có Bug mẫu liên kết với requirement/test case và đầy đủ evidence.
- [ ] Có ví dụ EP, BVA, Decision Table, State Transition, Use Case và Error Guessing.
- [ ] Có 80–120 manual test case chất lượng.
- [ ] Có Postman Collection và JWT tự động.
- [ ] Có JSON Schema và Newman report.
- [ ] Có SQL verification scripts.
- [ ] Có Playwright TypeScript framework và POM.
- [ ] Có fixtures và test data strategy.
- [ ] Có 25–40 automation test quan trọng.
- [ ] Chạy Chromium và Firefox.
- [ ] Có screenshot, video và trace khi fail.
- [ ] Có GitHub Actions.
- [ ] Có README với lệnh chạy một dòng.
- [ ] Có Test Summary, Defect Summary và known limitations.
- [ ] Hiểu Agile/Scrum, User Story và Acceptance Criteria.
- [ ] Biết review requirement và đặt câu hỏi làm rõ với BA/Developer.
- [ ] Có nền tảng JavaScript/TypeScript, OOP và xử lý dữ liệu cơ bản.
- [ ] Biết dùng Browser DevTools, Network, Console và đọc log.
- [ ] Biết Swagger/OpenAPI, Linux command line và Docker cơ bản.
- [ ] Có thể viết bug, test summary và trao đổi công việc bằng tiếng Anh cơ bản.
- [ ] Có thể demo và giải thích mà không đọc nguyên văn code.

---

## 16. Quy tắc mentoring

Mỗi phần thực hiện theo thứ tự:

1. Học khái niệm ngắn gọn.
2. Kiểm tra lại bằng câu hỏi.
3. Áp dụng vào một module Smart Shift.
4. Người học tự viết bản đầu.
5. Mentor review và chỉnh sửa.
6. Tự động hóa test phù hợp.
7. Chạy test và đọc kết quả.
8. Cập nhật RTM.
9. Lưu evidence.
10. Ghi nội dung cho báo cáo và phỏng vấn.

Không chuyển bước khi chưa hiểu expected result, chưa xác định kỹ thuật test, test chưa chạy lặp lại, chưa đọc report khi fail hoặc chưa cập nhật tài liệu/evidence.

---

## 17. Bước bắt đầu ngay

1. Tạo cấu trúc thư mục `qa-automation/`.
2. Viết `TEST_STRATEGY.md`.
3. Viết Test Scope theo module.
4. Tạo Jira project/board và Bug workflow.
5. Tạo RTM và RBAC Matrix.
6. Phân tích Authentication bằng EP và Error Guessing.
7. Phân tích User form bằng EP và BVA.
8. Thiết kế Decision Table cho RBAC.
9. Viết 15–20 test case đầu tiên.
10. Tạo Bug mẫu và link với requirement/test case.
11. Review test case trước khi tạo Postman Collection.

Module bắt đầu: **Authentication và RBAC**.

Lý do:

- Mọi API/UI test khác phụ thuộc login và JWT.
- Áp dụng được EP, Decision Table, State Transition và Error Guessing.
- Là module rủi ro cao và phù hợp làm smoke test đầu tiên.
- Có giá trị lớn khi phỏng vấn Junior QA.

---

## 18. Kỹ năng bổ sung theo yêu cầu Junior Tester/Automation Tester

Các phần trước tập trung vào quy trình và công cụ kiểm thử. Để sẵn sàng ứng tuyển Junior, cần bổ sung các nhóm năng lực sau.

### 18.1. Agile và Scrum

Phải hiểu:

- Agile mindset và phát triển lặp.
- Product Backlog và Sprint Backlog.
- Sprint Planning, Daily Scrum, Review và Retrospective.
- Definition of Ready và Definition of Done.
- User Story và Acceptance Criteria.
- Vai trò Product Owner, Scrum Master, Developer và QA.
- QA tham gia sớm thay vì chỉ nhận build để test.

Thực hành với Smart Shift:

- Chuyển một requirement thành User Story.
- Viết Acceptance Criteria theo Given–When–Then.
- Tách test task cho manual, API, database và UI automation.
- Ước lượng effort kiểm thử tương đối.
- Mô phỏng Daily Update: đã làm, sẽ làm và blocker.

### 18.2. Requirement Review và tư duy đặt câu hỏi

Junior QA phải biết phát hiện requirement thiếu, mâu thuẫn hoặc không thể kiểm thử.

Checklist review:

- Actor là ai?
- Preconditions là gì?
- Happy path, alternative flow và exception flow là gì?
- Input hợp lệ/không hợp lệ và giới hạn là gì?
- Expected result có đo lường được không?
- Role nào được phép thao tác?
- Resource thuộc owner/location nào?
- Trạng thái trước và sau thao tác là gì?
- Có tác động notification, audit hoặc database không?
- Điều gì xảy ra khi request lặp hoặc hai người thao tác đồng thời?

Thực hành:

- Viết ít nhất 10 câu hỏi làm rõ cho Auto Schedule.
- Viết ít nhất 10 câu hỏi làm rõ cho Attendance GPS.
- Ghi assumption và open question trong Test Plan.

### 18.3. JavaScript/TypeScript và OOP nền tảng

Ngoài cú pháp TypeScript đủ dùng cho Playwright, cần hiểu:

- Variable scope và immutable/mutable data.
- Function, callback, Promise và async/await.
- Array methods: `map`, `filter`, `find`, `some`, `every`, `reduce`.
- Object, destructuring và spread operator.
- Module import/export.
- Exception handling.
- Class, object, constructor, encapsulation và inheritance ở mức cơ bản.
- Interface và abstraction.
- JSON parse/stringify.
- Date/time handling.
- Regular expression cơ bản.
- Thuật toán đơn giản: tìm kiếm, sắp xếp, loại duplicate và đếm dữ liệu.

Thực hành:

- Viết utility tạo test data.
- Viết hàm tính expected payroll độc lập với UI.
- Viết hàm lọc test theo role/status.
- Parse API response và kiểm tra dữ liệu.

### 18.4. Web fundamentals và Browser DevTools

Phải hiểu:

- HTML, DOM, form, input, button, table và modal.
- CSS selector cơ bản.
- Cookie, localStorage và sessionStorage.
- Same-origin policy và CORS ở mức cơ bản.
- HTTP request lifecycle.
- Client-side và server-side validation.

DevTools phải biết dùng:

- Elements để kiểm tra DOM và accessibility role.
- Network để đọc request, response, payload, header và timing.
- Console để đọc JavaScript error.
- Application để kiểm tra token/storage.
- Disable cache và network throttling.
- Copy request dưới dạng cURL khi cần debug.

Thực hành:

- Trace request Login từ UI đến API.
- Kiểm tra JWT được lưu ở đâu.
- Xác định request gây lỗi khi một form submit thất bại.
- So sánh UI error với API error response.

### 18.5. Swagger/OpenAPI và API documentation

Phải biết:

- Đọc endpoint, method, parameter và request body.
- Đọc response schema và status code.
- Phân biệt required/optional field.
- Dùng Swagger UI để gửi request thử.
- Đối chiếu tài liệu API với hành vi thực tế.
- Nhận diện breaking change của API contract.

Mở rộng tùy chọn:

- SOAP/XML ở mức nhận biết.
- API contract testing.
- Mock server khi backend chưa sẵn sàng.

### 18.6. Linux command line, log và Docker

Linux/CLI cơ bản:

- `pwd`, `ls`, `cd`, `cat`, `less`, `head`, `tail`.
- `grep`, `find`, `curl` và biến môi trường.
- Exit code và pipe ở mức cơ bản.

Log troubleshooting:

- Đọc timestamp, level, stack trace và request context.
- Tìm correlation/request ID nếu hệ thống có.
- Phân biệt lỗi frontend, backend, database và tích hợp.
- Không đưa secret vào log hoặc bug report.

Docker cơ bản:

- Image, container, volume, network và port mapping.
- `docker compose up`, `ps`, `logs`, `down`.
- Health check.
- Environment variable.
- Kiểm tra service nào gây lỗi trong hệ thống nhiều container.

### 18.7. Test Environment và Test Data Management

Phải biết:

- Phân biệt local, development, test, staging và production.
- Không dùng dữ liệu production thật nếu chưa được phép/masking.
- Seed data, synthetic data và reusable baseline data.
- Data setup/cleanup.
- Tránh test phụ thuộc thứ tự và dữ liệu dùng chung.
- Đặt tên dữ liệu automation bằng prefix/suffix nhận diện được.
- Quản lý secret bằng environment variable hoặc CI secret.

Thực hành:

- Tạo bộ tài khoản test cho ba role.
- Tạo data factory cho user, period, shift và request.
- Viết cleanup strategy không xóa nhầm dữ liệu.
- Viết Environment Readiness Checklist.

### 18.8. Mock, Stub và Service Virtualization cơ bản

Phải hiểu:

- Khi nào cần mock/stub.
- Mock khác fake và stub thế nào ở mức cơ bản.
- Không mock phần đang là mục tiêu của test.
- Dùng Playwright route interception cho trường hợp frontend cần kiểm tra lỗi API.
- Mô phỏng timeout, 401, 403, 500 và response chậm.
- Mô phỏng Lark unavailable mà không phụ thuộc hệ thống thật.

### 18.9. Metrics, estimation và release readiness

Metrics cơ bản:

- Test execution progress.
- Pass/fail/block rate.
- Requirement coverage.
- Automation coverage.
- Defect theo severity, module và trạng thái.
- Defect reopen rate.
- Flaky test count.
- Pipeline duration.

Không dùng số lượng test case làm thước đo duy nhất.

Phải biết đưa ra release recommendation:

- Go.
- Go with known risks.
- No-go.

Kết luận phải dựa trên business risk, test result, defect và coverage.

### 18.10. Tiếng Anh chuyên ngành và kỹ năng mềm

Tiếng Anh tối thiểu:

- Đọc requirement, API documentation và error message.
- Viết test case và bug report ngắn gọn.
- Trình bày Daily Update.
- Hỏi lại requirement chưa rõ.
- Giải thích defect bằng evidence.
- Đọc tài liệu Playwright/Postman/GitHub Actions.

Từ vựng phải dùng được:

- Preconditions, reproduction steps, actual result, expected result.
- Severity, priority, regression, blocker và workaround.
- Intermittent/flaky, reproducible, environment-specific.
- Root cause, impact, scope và release risk.

Kỹ năng mềm:

- Giao tiếp không đổ lỗi.
- Phân tích và tư duy phản biện.
- Chủ động làm rõ yêu cầu.
- Quản lý thời gian và ưu tiên theo rủi ro.
- Phối hợp với BA, Developer, DevOps và Product Owner.
- Trình bày ngắn gọn bằng dữ liệu và evidence.

### 18.11. Kỹ năng mở rộng — Nice to Have

Không cần học sâu trước khi hoàn thành roadmap chính:

- Xray, Zephyr hoặc TestRail.
- BDD/Gherkin/Cucumber.
- Mobile testing và Appium.
- k6/JMeter performance testing.
- Selenium hoặc Cypress để hiểu hệ sinh thái khác.
- Jenkins/GitLab CI/Azure DevOps.
- Microservices, message queue và contract testing.
- Cloud/AWS cơ bản.
- Accessibility testing.
- Visual regression testing.
- AI hỗ trợ tạo test, review code và phân tích defect.

### 18.12. Đầu ra bổ sung cho portfolio

- Một User Story và Acceptance Criteria mẫu.
- Requirement Review Checklist và danh sách open questions.
- Environment Readiness Checklist.
- Test Data Strategy.
- Một ví dụ dùng DevTools để tìm root cause.
- Một ví dụ mock API error bằng Playwright.
- Một Test Summary có metrics và release recommendation.
- Một Bug Report và Daily Update bằng tiếng Anh.

### 18.13. Nội dung đưa vào CV

Ví dụ sau khi đã thực hiện thật:

- Designed manual test cases using EP, BVA, Decision Table, State Transition and Use Case techniques for a workforce scheduling system.
- Automated REST API tests with Postman/Newman and validated PostgreSQL data using SQL.
- Built a Playwright TypeScript framework using Page Object Model, fixtures and role-based authentication states.
- Integrated automated smoke/regression tests into GitHub Actions with HTML reports, traces and screenshots.
- Managed defects and test progress in Jira using structured workflows, filters and dashboards.

Chỉ đưa kỹ năng hoặc số liệu vào CV sau khi đã có artifact và có thể giải thích khi phỏng vấn.
