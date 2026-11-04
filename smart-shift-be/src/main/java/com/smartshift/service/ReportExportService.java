package com.smartshift.service;

import com.smartshift.dto.attendance.AttendanceResponse;
import com.smartshift.dto.payroll.PayrollRecordResponse;
import com.smartshift.enums.AttendanceStatus;
import com.smartshift.enums.PayrollStatus;
import com.smartshift.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReportExportService {
    public static final int MAX_ROWS = 100_000;
    private final AttendanceService attendanceService;
    private final PayrollService payrollService;
    private final Clock clock;

    public byte[] attendance(String username, LocalDate start, LocalDate end, Long locationId, AttendanceStatus status) {
        // Reuse the same date validation, branch scope and filters as the management table.
        var records=attendanceService.getAttendances(username,start,end,locationId,status);
        limit(records.size());
        try (Excel book=new Excel()) {
            book.info("Bảng chấm công",username,start,end,locationId,
                "Trạng thái: "+(status==null ? "Tất cả" : attendanceStatus(status)),clock.instant(),
                "Tổng hợp giờ gồm cả bản công chưa duyệt; lương chỉ dùng công đã duyệt.");
            Sheet detail=book.sheet("Chấm công","Mã chấm công","Mã phân công","Mã nhân viên","Họ tên","Chi nhánh","Múi giờ",
                "Ngày ca","Ca bắt đầu","Ca kết thúc","Vào thực tế","Ra thực tế","Nghỉ (phút)","Giờ công (phút)","Giờ công (giờ)",
                "Đi trễ (phút)","Về sớm (phút)","Tăng ca (phút)","Trạng thái","Đối soát","Người duyệt","Duyệt lúc",
                "Thiết bị","Mạng","IP vào","IP ra","SSID vào","SSID ra","Ghi chú","Vào (UTC)","Ra (UTC)");
            Map<String,AttendanceTotal> totals=new LinkedHashMap<>();
            for (AttendanceResponse r:records) {
                ZoneId zone=ZoneId.of(r.timezone());
                book.row(detail,id(r.id()),id(r.shiftAssignmentId()),r.employeeCode(),r.employeeName(),r.locationName(),r.timezone(),
                    r.workDate(),local(r.shiftStartAt(),zone),local(r.shiftEndAt(),zone),local(r.checkInAt(),zone),local(r.checkOutAt(),zone),
                    r.breakMinutes(),r.actualMinutes(),hours(r.actualMinutes()),r.lateMinutes(),r.earlyLeaveMinutes(),r.overtimeMinutes(),
                    attendanceStatus(r.status()),r.approvedAt()==null ? "Chưa duyệt" : "Đã duyệt",r.approvedByName(),local(r.approvedAt(),zone),
                    r.unverifiedDevice() ? "Chưa xác minh" : "Đã xác minh",r.unverifiedWifi() ? "Chưa xác minh" : "Đạt chính sách",
                    r.checkInIpAddress(),r.checkOutIpAddress(),r.checkInWifiSsid(),r.checkOutWifiSsid(),r.note(),utc(r.checkInAt()),utc(r.checkOutAt()));
                var total=totals.computeIfAbsent(r.locationId()+":"+r.employeeId(),k -> new AttendanceTotal(r));
                total.count++; total.minutes+=r.actualMinutes()==null ? 0 : r.actualMinutes();
                if(r.approvedAt()!=null) total.approved++;
                if(r.checkOutAt()==null) total.open++;
                if(r.unverifiedDevice()) total.device++;
                if(r.unverifiedWifi()) total.network++;
            }
            book.filter(detail);
            Sheet summary=book.sheet("Tổng hợp","Mã nhân viên","Họ tên","Chi nhánh","Số bản công","Giờ công (phút)",
                "Giờ công (giờ)","Đã duyệt","Chưa có giờ ra","Thiết bị chưa xác minh","Mạng chưa xác minh");
            for(var t:totals.values()) book.row(summary,t.record.employeeCode(),t.record.employeeName(),t.record.locationName(),
                t.count,t.minutes,hours(t.minutes),t.approved,t.open,t.device,t.network);
            book.filter(summary);
            return book.bytes();
        } catch(IOException e) { throw new BusinessRuleException("Không tạo được file Excel, vui lòng thử lại"); }
    }

    public byte[] payroll(String username, LocalDate start, LocalDate end, Long locationId) {
        // Export existing snapshots only: do not calculate or confirm payroll during download.
        var records=payrollService.getPayrollRecords(username,locationId,start,end);
        limit(records.size());
        try(Excel book=new Excel()) {
            book.info("Bảng lương dự tính",username,start,end,locationId,"Kỳ lương khớp đúng ngày bắt đầu/kết thúc đã chọn",clock.instant(),
                "Lương nháp có thể thay đổi. Cần tính lại công phải được xử lý trước xác nhận. File không thay đổi trạng thái lương.");
            Sheet detail=book.sheet("Bảng lương","Mã bảng lương","Mã nhân viên","Họ tên","Vai trò","Vị trí","Chi nhánh",
                "Từ ngày","Đến ngày","Giờ công (phút)","Giờ công (giờ)","Mức lương cơ bản (VND)","Cơ sở trả lương","Hệ số",
                "Lương trước thưởng (VND)","Thưởng (VND)","Tổng lương dự tính (VND)","Trạng thái","Nguồn công",
                "Người tính","Người xác nhận","Xác nhận lúc (UTC)","Ghi chú thưởng");
            Map<Long,PayrollTotal> totals=new LinkedHashMap<>();
            for(PayrollRecordResponse r:records) {
                boolean confirmed=r.status()==PayrollStatus.CONFIRMED;
                book.row(detail,id(r.id()),r.employeeCode(),r.employeeName(),"ROLE_MANAGER".equals(r.roleName()) ? "Quản lý" : "Nhân viên",
                    r.positionName(),r.locationName(),r.periodStart(),r.periodEnd(),r.workedMinutes(),hours(r.workedMinutes()),r.basePayAmount(),
                    "MONTHLY".equals(r.payBasis()) ? "Theo tháng" : "Theo giờ",r.salaryCoefficient(),r.baseAmount(),r.bonusAmount(),r.totalAmount(),
                    confirmed ? "Đã xác nhận" : "Nháp",r.sourceChanged() ? "Cần tính lại công" : "Theo lần tính đã lưu",
                    r.calculatedByName(),r.confirmedByName(),utc(r.confirmedAt()),r.bonusNote());
                var total=totals.computeIfAbsent(r.locationId(),k -> new PayrollTotal(r.locationName()));
                total.count++; total.minutes+=r.workedMinutes();
                if(confirmed) total.confirmed++; else total.draft++;
                if(r.sourceChanged()) total.stale++;
                total.base=total.base.add(r.baseAmount()); total.bonus=total.bonus.add(r.bonusAmount()); total.total=total.total.add(r.totalAmount());
            }
            book.filter(detail);
            Sheet summary=book.sheet("Tổng hợp","Chi nhánh","Số bảng lương","Nháp","Đã xác nhận","Cần tính lại công",
                "Giờ công (phút)","Giờ công (giờ)","Lương trước thưởng (VND)","Thưởng (VND)","Tổng lương dự tính (VND)");
            for(var t:totals.values()) book.row(summary,t.name,t.count,t.draft,t.confirmed,t.stale,t.minutes,hours(t.minutes),t.base,t.bonus,t.total);
            book.filter(summary);
            return book.bytes();
        } catch(IOException e) { throw new BusinessRuleException("Không tạo được file Excel, vui lòng thử lại"); }
    }

    private static void limit(int rows) {
        if(rows>MAX_ROWS) throw new BusinessRuleException("Chỉ xuất tối đa "+MAX_ROWS+" bản ghi mỗi lần; hãy thu hẹp bộ lọc");
    }
    private static String id(Long value) { return value==null ? null : value.toString(); }
    private static String utc(Instant value) { return value==null ? null : value.toString(); }
    private static LocalDateTime local(Instant value,ZoneId zone) { return value==null ? null : value.atZone(zone).toLocalDateTime(); }
    private static BigDecimal hours(Number minutes) {
        return minutes==null ? null : BigDecimal.valueOf(minutes.longValue()).divide(BigDecimal.valueOf(60),2,RoundingMode.HALF_UP);
    }
    private static String attendanceStatus(AttendanceStatus value) {
        if(value==null) return "";
        return switch(value) { case PRESENT -> "Đúng giờ"; case LATE -> "Đi trễ"; case EARLY_LEAVE -> "Về sớm"; case ABSENT -> "Vắng mặt"; };
    }
    private static final class AttendanceTotal {
        final AttendanceResponse record;
        int count,approved,open,device,network; long minutes;
        AttendanceTotal(AttendanceResponse record) { this.record=record; }
    }
    private static final class PayrollTotal {
        final String name; int count,draft,confirmed,stale; long minutes;
        BigDecimal base=BigDecimal.ZERO,bonus=BigDecimal.ZERO,total=BigDecimal.ZERO;
        PayrollTotal(String name) { this.name=name; }
    }

    /** Fixed row window and reused styles; no autosizing/font rendering on the headless server. */
    private static final class Excel implements AutoCloseable {
        final SXSSFWorkbook workbook=new SXSSFWorkbook(100);
        final Map<Sheet,Integer> columns=new IdentityHashMap<>();
        final CellStyle header,text,integer,decimal,date,time;
        Excel() {
            workbook.setCompressTempFiles(true);
            text=workbook.createCellStyle(); text.setWrapText(true); text.setVerticalAlignment(VerticalAlignment.TOP);
            integer=workbook.createCellStyle(); integer.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
            decimal=workbook.createCellStyle(); decimal.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
            date=workbook.createCellStyle(); date.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy"));
            time=workbook.createCellStyle(); time.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy hh:mm:ss"));
            header=workbook.createCellStyle(); header.setWrapText(true); header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font font=workbook.createFont(); font.setBold(true); font.setColor(IndexedColors.WHITE.getIndex()); header.setFont(font);
        }
        Sheet sheet(String name,String... columns) {
            Sheet sheet=workbook.createSheet(name); Row row=sheet.createRow(0); row.setHeightInPoints(32);
            this.columns.put(sheet,columns.length);
            for(int i=0;i<columns.length;i++) {
                Cell cell=row.createCell(i); cell.setCellValue(columns[i]); cell.setCellStyle(header);
                int width=columns[i].contains("Ghi chú") ? 55 : columns[i].contains("Họ tên") || columns[i].contains("nhánh") ? 28 : 22;
                sheet.setColumnWidth(i,width*256);
            }
            sheet.createFreezePane(0,1); sheet.getPrintSetup().setLandscape(true); sheet.setFitToPage(true);
            return sheet;
        }
        void row(Sheet sheet,Object... values) {
            Row row=sheet.createRow(sheet.getLastRowNum()+1);
            for(int i=0;i<values.length;i++) {
                Object value=values[i]; Cell cell=row.createCell(i);
                if(value==null) continue; // Missing timestamps/minutes must remain blank, not zero or a fake date.
                if(value instanceof LocalDate d) { cell.setCellValue(d); cell.setCellStyle(date); }
                else if(value instanceof LocalDateTime t) { cell.setCellValue(t); cell.setCellStyle(time); }
                else if(value instanceof BigDecimal amount) {
                    if(amount.stripTrailingZeros().precision()>15) { cell.setCellValue(amount.toPlainString()); cell.setCellStyle(text); }
                    else { cell.setCellValue(amount.doubleValue()); cell.setCellStyle(decimal); }
                } else if(value instanceof Number n) { cell.setCellValue(n.doubleValue()); cell.setCellStyle(integer); }
                else {
                    String string=value.toString();
                    if(string.length()>32767) {
                        int cut=32766;
                        if(Character.isHighSurrogate(string.charAt(cut-1))) cut--;
                        string=string.substring(0,cut)+"…";
                    }
                    cell.setCellValue(string); cell.setCellStyle(text); // Always a string, never an Excel formula or hyperlink.
                }
            }
        }
        void filter(Sheet sheet) { sheet.setAutoFilter(new CellRangeAddress(0,sheet.getLastRowNum(),0,columns.get(sheet)-1)); }
        void info(String title,String username,LocalDate start,LocalDate end,Long locationId,String filter,Instant at,String note) {
            Sheet sheet=sheet("Thông tin","Nội dung","Giá trị"); sheet.setColumnWidth(1,100*256);
            row(sheet,"Báo cáo",title); row(sheet,"Người xuất",username); row(sheet,"Xuất lúc (UTC)",utc(at));
            row(sheet,"Từ ngày",start); row(sheet,"Đến ngày",end);
            row(sheet,"Chi nhánh yêu cầu",locationId==null ? "Theo phạm vi tài khoản" : "Mã "+locationId);
            row(sheet,"Bộ lọc",filter); row(sheet,"Lưu ý",note);
            row(sheet,"Định dạng","Tiền/giờ tối đa 2 chữ số thập phân. Số thập phân vượt 15 chữ số có nghĩa xuất dạng văn bản để giữ giá trị. Ô văn bản trên 32.767 ký tự được rút gọn và thêm dấu ….");
        }
        byte[] bytes() throws IOException {
            ByteArrayOutputStream output=new ByteArrayOutputStream(); workbook.write(output); return output.toByteArray();
        }
        @Override public void close() throws IOException {
            try { workbook.close(); } finally { workbook.dispose(); }
        }
    }
}
