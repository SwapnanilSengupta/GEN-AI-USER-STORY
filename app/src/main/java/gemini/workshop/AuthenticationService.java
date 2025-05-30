package gemini.workshop;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

public class AuthenticationService {

    private static Properties dbProperties;

    static {
        dbProperties = new Properties();
        try (InputStream input = AuthenticationService.class.getClassLoader().getResourceAsStream("configuration.properties")) {
            if (input == null) {
                System.err.println("Unable to load configuration.properties");
                throw new RuntimeException("Unable to load configuration.properties");
            }
            dbProperties.load(input);
        } catch (IOException ex) {
            System.err.println("Error loading configuration.properties: " + ex.getMessage());
            throw new RuntimeException("Error loading configuration.properties", ex);
        }
    }

    private static String getDbProperty(String key) {
        return dbProperties.getProperty(key);
    }

    public static boolean authenticateUser(String username, String password) {
        try {
            // Load the PostgreSQL driver
            Class.forName("org.postgresql.Driver");

            // Set up connection properties for IAM authentication and the socket factory
            Properties properties = new Properties();
            properties.setProperty("user", getDbProperty("db.user"));
            properties.setProperty("password", getDbProperty("db.password")); // Password is not used for IAM
            properties.setProperty("cloudSqlInstance", getDbProperty("cloud.sql.instance"));
            properties.setProperty("enableIamAuth", "true");
            properties.setProperty("socketFactory", "com.google.cloud.sql.postgres.SocketFactory"); // Add the Socket Factory

            String dbName = getDbProperty("db.name");
            String jdbcUrlBase = getDbProperty("jdbc.url.base");
            String jdbcUrl = String.format(jdbcUrlBase, dbName);

            try (Connection conn = DriverManager.getConnection(jdbcUrl, properties);
                 PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM authentication WHERE username = ? AND password = ?")) {
                pstmt.setString(1, username);
                pstmt.setString(2, password); // WARNING: In a real app, compare hashed passwords!
                ResultSet rs = pstmt.executeQuery();
                return rs.next(); // Returns true if a matching user is found
            }
        } catch (ClassNotFoundException e) {
            System.err.println("PostgreSQL JDBC Driver not found!");
            e.printStackTrace();
            return false;
        } catch (SQLException e) {
            System.err.println("Database authentication error!");
            e.printStackTrace();
            return false;
        }
    }
}