package app.finwave.backend.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Utility class for initializing test databases with SQL scripts.
 */
public class TestDbInitializer {

    /**
     * Initializes a database with SQL from a file in the classpath.
     *
     * @param connection Database connection
     * @param scriptPath Path to SQL script file, relative to resources/db/
     */
    public static void initializeDb(Connection connection, String scriptPath) throws SQLException, IOException {
        List<String> statements = loadSqlStatements("db/" + scriptPath);
        
        try (Statement stmt = connection.createStatement()) {
            for (String sql : statements) {
                stmt.execute(sql);
            }
        }
    }

    /**
     * Loads SQL statements from a file in the resources directory.
     *
     * @param path Path to SQL file in classpath
     * @return List of SQL statements
     */
    private static List<String> loadSqlStatements(String path) throws IOException {
        List<String> statements = new ArrayList<>();
        StringBuilder currentStatement = new StringBuilder();
        
        try (InputStream is = TestDbInitializer.class.getClassLoader().getResourceAsStream(path);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                // Skip comments and empty lines
                line = line.trim();
                if (line.isEmpty() || line.startsWith("--")) {
                    continue;
                }
                
                currentStatement.append(line).append(" ");
                
                // If the line ends with a semicolon, it's the end of a statement
                if (line.endsWith(";")) {
                    statements.add(currentStatement.toString());
                    currentStatement = new StringBuilder();
                }
            }
            
            // If there's any statement left without a semicolon
            String remaining = currentStatement.toString().trim();
            if (!remaining.isEmpty()) {
                statements.add(remaining);
            }
        }
        
        return statements;
    }
    
    /**
     * Example of how to use this in a test:
     * 
     * <pre>
     * &#64;Before
     * public void setUp() throws Exception {
     *     // Get a connection, perhaps using H2
     *     Connection conn = DriverManager.getConnection("jdbc:h2:mem:testdb", "sa", "");
     *     
     *     // Initialize with schema
     *     TestDbInitializer.initializeDb(conn, "init-schema.sql");
     *     
     *     // Then add test data if needed
     *     TestDbInitializer.initializeDb(conn, "test-data.sql");
     * }
     * </pre>
     */
} 