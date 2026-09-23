import java.sql.*;

public class Conn {
    Connection C;
    Statement S;

    // Settings come from environment variables so no password is stored in the code.
    // Defaults work for a local MySQL where root has no password.
    public Conn() {
        String url = env("DB_URL", "jdbc:mysql://localhost:3306/bankmanagementsystem");
        String user = env("DB_USER", "root");
        String password = env("DB_PASSWORD", "");
        try {
            C = DriverManager.getConnection(url, user, password);
            S = C.createStatement();
        } catch (Exception e) {
            System.out.println("Could not connect to the database: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return (value == null || value.isEmpty()) ? fallback : value;
    }
}
