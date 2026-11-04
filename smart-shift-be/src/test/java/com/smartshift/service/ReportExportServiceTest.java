package com.smartshift.service;

import com.smartshift.dto.attendance.AttendanceResponse;
import com.smartshift.dto.payroll.PayrollRecordResponse;
import com.smartshift.enums.*;
import com.smartshift.exception.BusinessRuleException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportExportServiceTest {
    @Mock AttendanceService attendance;
    @Mock PayrollService payroll;
    ReportExportService service;
    static final LocalDate START=LocalDate.of(2026,11,1),END=LocalDate.of(2026,11,30);
    @BeforeEach void setup() {
        service=new ReportExportService(attendance,payroll,Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"),ZoneOffset.UTC));
    }
    AttendanceResponse attendanceRow() {
        var r=mock(AttendanceResponse.class);
        when(r.timezone()).thenReturn("Asia/Ho_Chi_Minh");
        when(r.employeeId()).thenReturn(3L); when(r.locationId()).thenReturn(2L);
        when(r.employeeCode()).thenReturn("EMP001"); when(r.employeeName()).thenReturn("Nguyễn Văn An");
        when(r.locationName()).thenReturn("Chi nhánh Việt Nam");
        return r;
    }
    PayrollRecordResponse payrollRow() {
        var r=mock(PayrollRecordResponse.class);
        when(r.locationId()).thenReturn(2L); when(r.locationName()).thenReturn("Chi nhánh Việt Nam");
        when(r.workedMinutes()).thenReturn(450);
        when(r.baseAmount()).thenReturn(new BigDecimal("300000.25"));
        when(r.bonusAmount()).thenReturn(new BigDecimal("50000.25"));
        when(r.totalAmount()).thenReturn(new BigDecimal("350000.50"));
        return r;
    }
    byte[] attendanceFile(List<AttendanceResponse> rows) {
        when(attendance.getAttendances("manager",START,END,2L,AttendanceStatus.LATE)).thenReturn(rows);
        return service.attendance("manager",START,END,2L,AttendanceStatus.LATE);
    }
    byte[] payrollFile(List<PayrollRecordResponse> rows) {
        when(payroll.getPayrollRecords("manager",2L,START,END)).thenReturn(rows);
        return service.payroll("manager",START,END,2L);
    }
    Cell cell(Sheet sheet,int row,String header) {
        for(Cell c:sheet.getRow(0)) if(c.getStringCellValue().equals(header)) return sheet.getRow(row).getCell(c.getColumnIndex());
        throw new AssertionError("Missing column: "+header);
    }
    @Test void workbookHasMetadataHeadersAndEmptyReportWithoutFakeRows() throws Exception {
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(attendanceFile(List.of())))) {
            assertEquals(3,book.getNumberOfSheets());
            assertEquals("Người xuất",book.getSheet("Thông tin").getRow(2).getCell(0).getStringCellValue());
            assertEquals("manager",book.getSheet("Thông tin").getRow(2).getCell(1).getStringCellValue());
            assertEquals(0,book.getSheet("Chấm công").getLastRowNum());
            assertEquals(0,book.getSheet("Tổng hợp").getLastRowNum());
            assertTrue(book.getSheet("Chấm công").getPaneInformation().isFreezePane());
            assertTrue(book.getSheet("Chấm công").getCTWorksheet().isSetAutoFilter());
        }
    }
    @Test void attendancePreservesBranchDatesOvernightAndLiteralFormulaLikeText() throws Exception {
        var r=attendanceRow();
        when(r.workDate()).thenReturn(START);
        when(r.checkInAt()).thenReturn(Instant.parse("2026-11-01T15:00:00Z"));
        when(r.checkOutAt()).thenReturn(Instant.parse("2026-11-01T23:00:00Z"));
        when(r.actualMinutes()).thenReturn(450);
        when(r.note()).thenReturn("=HYPERLINK(\"https://example.test\",\"Tên tiếng Việt\")");
        when(r.unverifiedWifi()).thenReturn(true);
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(attendanceFile(List.of(r))))) {
            var sheet=book.getSheet("Chấm công");
            assertEquals("Nguyễn Văn An",cell(sheet,1,"Họ tên").getStringCellValue());
            assertEquals(LocalDateTime.of(2026,11,1,22,0),cell(sheet,1,"Vào thực tế").getLocalDateTimeCellValue());
            assertEquals(LocalDateTime.of(2026,11,2,6,0),cell(sheet,1,"Ra thực tế").getLocalDateTimeCellValue());
            assertEquals(7.5,cell(sheet,1,"Giờ công (giờ)").getNumericCellValue());
            assertEquals(CellType.STRING,cell(sheet,1,"Ghi chú").getCellType());
            assertTrue(cell(sheet,1,"Ghi chú").getStringCellValue().startsWith("=HYPERLINK"));
            assertEquals("Chưa xác minh",cell(sheet,1,"Mạng").getStringCellValue());
            assertEquals("2026-11-01T23:00:00Z",cell(sheet,1,"Ra (UTC)").getStringCellValue());
        }
        verify(attendance).getAttendances("manager",START,END,2L,AttendanceStatus.LATE);
        verifyNoMoreInteractions(attendance);
    }
    @Test void missingAttendanceValuesStayBlankAndDoNotCountAsApproved() throws Exception {
        var r=attendanceRow();
        when(r.actualMinutes()).thenReturn(null);
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(attendanceFile(List.of(r))))) {
            assertEquals(CellType.BLANK,cell(book.getSheet("Chấm công"),1,"Giờ công (phút)").getCellType());
            assertEquals(CellType.BLANK,cell(book.getSheet("Chấm công"),1,"Vào thực tế").getCellType());
            var summary=book.getSheet("Tổng hợp");
            assertEquals(0,cell(summary,1,"Đã duyệt").getNumericCellValue());
            assertEquals(1,cell(summary,1,"Chưa có giờ ra").getNumericCellValue());
        }
    }
    @Test void streamingWindowDoesNotLoseHeadersAndTextFitsExcelLimit() throws Exception {
        String longNote=new Random(42).ints(40000,33,127)
            .collect(StringBuilder::new,StringBuilder::appendCodePoint,StringBuilder::append).toString();
        var r=attendanceRow(); when(r.note()).thenReturn(longNote); when(r.actualMinutes()).thenReturn(450);
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(attendanceFile(Collections.nCopies(150,r))))) {
            var detail=book.getSheet("Chấm công");
            assertEquals(150,detail.getLastRowNum());
            assertTrue(cell(detail,150,"Ghi chú").getStringCellValue().length()<=32767);
            assertTrue(cell(detail,150,"Ghi chú").getStringCellValue().endsWith("…"));
            assertEquals(67500,cell(book.getSheet("Tổng hợp"),1,"Giờ công (phút)").getNumericCellValue());
            assertTrue(book.getNumCellStyles()<20,"Styles must be reused, not created for every row");
        }
    }
    @Test void payrollExportsSavedDraftConfirmedAndStaleStateWithoutRecalculation() throws Exception {
        var draft=payrollRow(); when(draft.status()).thenReturn(PayrollStatus.DRAFT); when(draft.sourceChanged()).thenReturn(true);
        var confirmed=payrollRow(); when(confirmed.status()).thenReturn(PayrollStatus.CONFIRMED);
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(payrollFile(List.of(draft,confirmed))))) {
            var detail=book.getSheet("Bảng lương");
            assertEquals("Nháp",cell(detail,1,"Trạng thái").getStringCellValue());
            assertEquals("Cần tính lại công",cell(detail,1,"Nguồn công").getStringCellValue());
            assertEquals("Đã xác nhận",cell(detail,2,"Trạng thái").getStringCellValue());
            var summary=book.getSheet("Tổng hợp");
            assertEquals(1,cell(summary,1,"Nháp").getNumericCellValue());
            assertEquals(1,cell(summary,1,"Đã xác nhận").getNumericCellValue());
            assertEquals(1,cell(summary,1,"Cần tính lại công").getNumericCellValue());
            assertEquals(700001,cell(summary,1,"Tổng lương dự tính (VND)").getNumericCellValue());
        }
        verify(payroll).getPayrollRecords("manager",2L,START,END);
        verifyNoMoreInteractions(payroll);
    }
    @Test void amountBeyondExcelNumericPrecisionIsPreservedAsText() throws Exception {
        var r=payrollRow(); when(r.totalAmount()).thenReturn(new BigDecimal("123456789012345.67"));
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(payrollFile(List.of(r))))) {
            var amount=cell(book.getSheet("Bảng lương"),1,"Tổng lương dự tính (VND)");
            assertEquals(CellType.STRING,amount.getCellType()); assertEquals("123456789012345.67",amount.getStringCellValue());
        }
    }
    @Test @SuppressWarnings("unchecked") void excessiveRowsAreRejectedBeforeGeneratingWorkbook() {
        List<AttendanceResponse> huge=mock(List.class); when(huge.size()).thenReturn(ReportExportService.MAX_ROWS+1);
        when(attendance.getAttendances("manager",START,END,2L,AttendanceStatus.LATE)).thenReturn(huge);
        assertThrows(BusinessRuleException.class,()->service.attendance("manager",START,END,2L,AttendanceStatus.LATE));
        verify(huge,never()).iterator();
    }
}
