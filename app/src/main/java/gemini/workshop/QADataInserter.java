package gemini.workshop;

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.io.InputStream;

public class QADataInserter {

    private static Properties dbProperties;

    static {
        dbProperties = new Properties();
        try (InputStream input = QADataInserter.class.getClassLoader()
                .getResourceAsStream("configuration.properties")) {
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

    public static void insertQAData(String UserStory, String TestCase) {
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
                                                                                                            
            // Establish the connection using the Google Cloud SQL JDBC driver
            String dbName = getDbProperty("db.name");
            String jdbcUrlBase = getDbProperty("jdbc.url.base");
            String jdbcUrl = String.format(jdbcUrlBase, dbName);
            Connection connection = DriverManager.getConnection(jdbcUrl, properties);

            if (connection != null) {
                System.out.println("Connected to PostgreSQL database successfully using IAM!");

                // Prepare the SQL statement for insertion with the current date
                String insertSQL = "INSERT INTO UserStoryOutput (Date,UserStory,TestCase) VALUES (?, ?, ?)";
                PreparedStatement preparedStatement = connection.prepareStatement(insertSQL);

                // Get the current date as a string
                LocalDate currentDate = LocalDate.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                String currentDateString = currentDate.format(formatter);

                // Insert the question, answer, and current date
                if (UserStory != null && TestCase != null) {
                    preparedStatement.setString(1, currentDateString);
                    preparedStatement.setString(2, UserStory);
                    preparedStatement.setString(3, TestCase);
                    int rowsAffected = preparedStatement.executeUpdate();
                    if (rowsAffected > 0) {
                        System.out.println("UserStory: " + UserStory + " - TestCase inserted successfully on "
                                + currentDateString + "!");
                    } else {
                        System.out.println("Failed to insert: UserStory - " + UserStory);
                    }
                } else {
                    System.out.println("Error: UserStory or TestCase is null.");
                }

                // Close resources
                preparedStatement.close();
                connection.close();
            } else {
                System.out.println("Failed to connect to PostgreSQL using IAM.");
            }

        } catch (ClassNotFoundException e) {
            System.err.println("PostgreSQL JDBC Driver not found!");
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("Connection failed or data insertion error!");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException, URISyntaxException {
        try {
            // Load the PostgreSQL driver
            Class.forName("org.postgresql.Driver");

            // Set up connection properties for IAM authentication and the socket factory
            Properties properties = new Properties();
            properties.setProperty("user", getDbProperty("db.user"));
            properties.setProperty("password", getDbProperty("db.password")); // For local testing if needed
            properties.setProperty("cloudSqlInstance", getDbProperty("cloud.sql.instance"));
            properties.setProperty("enableIamAuth", "true");
            properties.setProperty("socketFactory", "com.google.cloud.sql.postgres.SocketFactory"); // Add the Socket Factory
                                                                                                     

            // Establish the connection using the Google Cloud SQL JDBC driver
            String dbName = getDbProperty("db.name");
            String jdbcUrlBase = getDbProperty("jdbc.url.base");
            String jdbcUrl = String.format(jdbcUrlBase, dbName);
            Connection connection = DriverManager.getConnection(jdbcUrl, properties);

            if (connection != null) {
                System.out.println("Successfully connected to PostgreSQL using IAM for testing!");
                connection.close();
            } else {
                System.out.println("Failed to connect to PostgreSQL using IAM for testing.");
            }

        } catch (ClassNotFoundException e) {
            System.err.println("PostgreSQL JDBC Driver not found!");
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("Connection failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}