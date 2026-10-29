package server;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý xác thực người dùng (Authentication)
 * Đề tài: Hệ thống FTP Server/Client cơ bản với Hai kênh
 * Thực hiện: Trần Nhật Tiến (MSSV: 2380602243)
 */
public class UserManager {
    // Lưu trữ tài khoản: username -> password
    private final Map<String, String> userDatabase = new ConcurrentHashMap<>();

    public UserManager() {
        initDefaultUsers();
    }

    /**
     * Khởi tạo các tài khoản mặc định phục vụ kiểm thử đề tài
     */
    private void initDefaultUsers() {
        // Tài khoản của 3 thành viên trong nhóm 11
        userDatabase.put("tien", "123456");      // Trần Nhật Tiến
        userDatabase.put("vu", "123456");        // Nguyễn Anh Vũ
        userDatabase.put("trong", "123456");     // Phan Thanh Trọng

        // Tài khoản quản trị và kiểm thử
        userDatabase.put("admin", "admin123");
        userDatabase.put("test", "test");
        userDatabase.put("anonymous", "anonymous");
    }

    /**
     * Kiểm tra username có tồn tại trên server không
     */
    public boolean userExists(String username) {
        if (username == null) return false;
        return userDatabase.containsKey(username.trim().toLowerCase());
    }

    /**
     * Kiểm tra tính hợp lệ của tài khoản và mật khẩu
     */
    public boolean authenticate(String username, String password) {
        if (username == null || password == null) return false;
        String normalizedUser = username.trim().toLowerCase();

        // Tài khoản khách vô danh
        if ("anonymous".equalsIgnoreCase(normalizedUser)) {
            return true;
        }

        String storedPassword = userDatabase.get(normalizedUser);
        return storedPassword != null && storedPassword.equals(password);
    }

    /**
     * Thêm tài khoản mới trong phiên hoạt động của server
     */
    public void addUser(String username, String password) {
        if (username != null && password != null) {
            userDatabase.put(username.trim().toLowerCase(), password);
        }
    }

    /**
     * Xóa tài khoản khỏi hệ thống
     */
    public boolean removeUser(String username) {
        if (username == null) return false;
        return userDatabase.remove(username.trim().toLowerCase()) != null;
    }

    /**
     * Lấy danh sách tất cả các username hiện có
     */
    public Map<String, String> getAllUsers() {
        return Collections.unmodifiableMap(userDatabase);
    }
}
