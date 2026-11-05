package server;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UserManager {

    private final Map<String, String> userDatabase = new ConcurrentHashMap<>();

    public UserManager() {
        initDefaultUsers();
    }

    private void initDefaultUsers() {

        userDatabase.put("tien", "123456");
        userDatabase.put("vu", "123456");
        userDatabase.put("trong", "123456");

        userDatabase.put("admin", "admin123");
        userDatabase.put("test", "test");
        userDatabase.put("anonymous", "anonymous");
    }

    public boolean userExists(String username) {
        if (username == null) return false;
        return userDatabase.containsKey(username.trim().toLowerCase());
    }

    public boolean authenticate(String username, String password) {
        if (username == null || password == null) return false;
        String normalizedUser = username.trim().toLowerCase();

        if ("anonymous".equalsIgnoreCase(normalizedUser)) {
            return true;
        }

        String storedPassword = userDatabase.get(normalizedUser);
        return storedPassword != null && storedPassword.equals(password);
    }

    public void addUser(String username, String password) {
        if (username != null && password != null) {
            userDatabase.put(username.trim().toLowerCase(), password);
        }
    }

    public boolean removeUser(String username) {
        if (username == null) return false;
        return userDatabase.remove(username.trim().toLowerCase()) != null;
    }

    public Map<String, String> getAllUsers() {
        return Collections.unmodifiableMap(userDatabase);
    }
}
