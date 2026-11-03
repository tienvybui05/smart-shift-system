-- V23: Add Wi-Fi verification and IP network fields to locations and attendances
ALTER TABLE locations
    ADD COLUMN IF NOT EXISTS wifi_ssid VARCHAR(100) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS wifi_bssid VARCHAR(50) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS allowed_ip_address VARCHAR(100) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS is_wifi_required BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN locations.wifi_ssid IS 'Tên mạng Wi-Fi tại chi nhánh (SSID)';
COMMENT ON COLUMN locations.wifi_bssid IS 'Địa chỉ phần cứng router Wi-Fi (BSSID / MAC)';
COMMENT ON COLUMN locations.allowed_ip_address IS 'Địa chỉ IP hoặc dải IP router cho phép chấm công';
COMMENT ON COLUMN locations.is_wifi_required IS 'Chi nhánh có bắt buộc kết nối Wi-Fi khi chấm công hay không';

ALTER TABLE attendances
    ADD COLUMN IF NOT EXISTS check_in_ip_address VARCHAR(50) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS check_in_wifi_ssid VARCHAR(100) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS check_out_ip_address VARCHAR(50) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS check_out_wifi_ssid VARCHAR(100) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS is_unverified_wifi BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN attendances.check_in_ip_address IS 'Địa chỉ IP khi check-in';
COMMENT ON COLUMN attendances.check_in_wifi_ssid IS 'Tên Wi-Fi khi check-in';
COMMENT ON COLUMN attendances.check_out_ip_address IS 'Địa chỉ IP khi check-out';
COMMENT ON COLUMN attendances.check_out_wifi_ssid IS 'Tên Wi-Fi khi check-out';
COMMENT ON COLUMN attendances.is_unverified_wifi IS 'Đánh dấu nếu chấm công không khớp mạng Wi-Fi của chi nhánh';
