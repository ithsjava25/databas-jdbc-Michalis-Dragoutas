package com.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Scanner;

public class Main {


    private String jdbcUrl;
    private String dbUser;
    private String dbPass;
    private Scanner scanner;

    public static void main(String[] args) {
        if (isDevMode(args)) {
            DevDatabaseInitializer.start();
        }
        new Main().run();
    }

    public void run() {
        // Resolve DB settings with precedence: System properties -> Environment variables
        jdbcUrl = resolveConfig("APP_JDBC_URL", "APP_JDBC_URL");
        dbUser = resolveConfig("APP_DB_USER", "APP_DB_USER");
        dbPass = resolveConfig("APP_DB_PASS", "APP_DB_PASS");

        if (jdbcUrl == null || dbUser == null || dbPass == null) {
            throw new IllegalStateException(
                    "Missing DB configuration. Provide APP_JDBC_URL, APP_DB_USER, APP_DB_PASS " +
                            "as system properties (-Dkey=value) or environment variables.");
        }


        scanner = new Scanner(System.in);

        try (Connection connection = DriverManager.getConnection(jdbcUrl, dbUser, dbPass)) {
            // Initial connection successful.
        } catch (SQLException e) {
            throw new RuntimeException("Initial DB connection failed.", e);
        }

        // Todo: Starting point for your code

        // Login Control
        if (!handleLogin()) {
            scanner.close();
            return; // Exit if login failed or choose 0
        }

        //  Menu Loop
        handleMenuLoop();

        //  Cleanup
        scanner.close();


    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, dbUser, dbPass);
    }

    // Login

    private boolean handleLogin() {
        int attempts = 0;
        final int maxAttempts = 3;

        while (attempts < maxAttempts) {
            System.out.print("Username: ");
            String username = scanner.nextLine();
            System.out.print("Password: ");
            String password = scanner.nextLine();

            try (Connection conn = getConnection()) {
                String sql = "SELECT COUNT(*) FROM account WHERE name = ? AND password = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, username);
                    stmt.setString(2, password);

                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            return true; // login successful
                        }
                    }
                }
            } catch (SQLException e) {
                System.err.println("Error accessing database during login: " + e.getMessage());
                return false;
            }

            attempts++;
            if (attempts < maxAttempts) {
                System.out.println("Invalid username or password. Try again (" + (maxAttempts - attempts) + " attempts left).");
            } else {
                System.out.println("Invalid username or password. Maximum attempts reached. Exiting.");
            }
        }

        return false; // all attempts failed
    }

    // Menu

    private void printMenu() {
        System.out.println("\n--- Menu ---");
        System.out.println("1) List moon missions");
        System.out.println("2) Get a moon mission by mission_id");
        System.out.println("3) Count missions for a given year");
        System.out.println("4) Create an account");
        System.out.println("5) Update an account password");
        System.out.println("6) Delete an account");
        System.out.println("0) Exit");
        System.out.println("------------");
    }

    private void handleMenuLoop() {
        boolean running = true;
        while (running) {
            printMenu();
            System.out.print("Enter choice (0-6): ");
            String choice = scanner.nextLine().trim();

            try {
                switch (choice) {
                    case "1": listMoonMissions(); break;
                    case "2": getMoonMissionById(); break;
                    case "3": countMissionsByYear(); break;
                    case "4": createAccount(); break;
                    case "5": updateAccountPassword(); break;
                    case "6": deleteAccount(); break;
                    case "0": running = false; break;
                    default: System.out.println("Invalid choice. Please select a number from 0 to 6.");
                }
            } catch (SQLException e) {
                System.err.println("Database operation failed: " + e.getMessage());
            } catch (NumberFormatException e) {
                System.err.println("Invalid number format provided for input.");
            }
        }
    }

    // Menu Options

    // Option 1
    private void listMoonMissions() throws SQLException {

        System.out.println("--- Spacecraft Names ---");
        String sql = "SELECT spacecraft FROM moon_mission ORDER BY launch_date";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                System.out.println(rs.getString("spacecraft"));
            }
        }
    }

    // Option 2
    private void getMoonMissionById() throws SQLException {

        System.out.print("Enter mission_id: ");
        long missionId = Long.parseLong(scanner.nextLine().trim());

        String sql = "SELECT * FROM moon_mission WHERE mission_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, missionId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    System.out.println("\n--- Mission Details (ID: " + missionId + ") ---");
                    System.out.println("Spacecraft: " + rs.getString("spacecraft"));
                    System.out.println("Launch Date: " + rs.getDate("launch_date"));
                    // Using 'outcome' instead of 'destination' based on init.sql schema
                    System.out.println("Outcome: " + rs.getString("outcome"));
                } else {
                    System.out.println("Mission with ID " + missionId + " not found.");
                }
            }
        }
    }


    // Option 3
    private void countMissionsByYear() throws SQLException {

        System.out.print("Enter year: ");
        int year = Integer.parseInt(scanner.nextLine().trim());

        if(year < 1950 || year > 2100) { System.out.println("Invalid year"); return; }

        String sql = "SELECT COUNT(*) FROM moon_mission WHERE YEAR(launch_date) = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, year);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt(1);
                    System.out.println("Total missions launched in " + year + ": " + count);
                }
            }
        }
    }


    // Option 4

    private boolean usernameExists(String username) throws SQLException {
        String sql = "SELECT 1 FROM account WHERE name = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next(); // true if a row exists
            }
        }
    }
    private void createAccount() throws SQLException {

        System.out.print("Enter first name: ");
        String firstName = scanner.nextLine();
        System.out.print("Enter last name: ");
        String lastName = scanner.nextLine();
        System.out.print("Enter ssn: ");
        String ssn = scanner.nextLine();
        System.out.print("Enter password: ");
        String password = scanner.nextLine();


        if (firstName.isEmpty() || lastName.isEmpty()) {
            System.out.println("First name and last name cannot be empty.");
            return;
        }

        if(password.isEmpty()) {
            System.out.println("Password cannot be empty.");
            return;
        }
        String baseUsername =
                firstName.substring(0, Math.min(firstName.length(), 3)) + lastName;

        String username = baseUsername;
        int suffix = 1;

        while (usernameExists(username)) {
            username = baseUsername + suffix;
            suffix++;
        }

        String sql = "INSERT INTO account (name, password, first_name, last_name, ssn) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            stmt.setString(2, password);
            stmt.setString(3, firstName);
            stmt.setString(4, lastName);
            stmt.setString(5, ssn);

            if (stmt.executeUpdate() > 0) {
                System.out.println("Account created successfully. Username: " + username);
            } else {
                System.out.println("Failed to create account.");
            }
        }
    }


    // Option 5
    private void updateAccountPassword() throws SQLException {

        System.out.print("Enter user_id: ");
        long userId = Long.parseLong(scanner.nextLine().trim());
        System.out.print("Enter new password: ");
        String newPassword = scanner.nextLine();

        String sql = "UPDATE account SET password = ? WHERE user_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, newPassword);
            stmt.setLong(2, userId);

            if (stmt.executeUpdate() > 0) {
                System.out.println("Account password updated successfully.");
            } else {
                System.out.println("Update failed: User ID " + userId + " not found.");
            }
        }
    }


    // Option 6
    private void deleteAccount() throws SQLException {

        System.out.print("Enter user_id to delete: ");
        long userId = Long.parseLong(scanner.nextLine().trim());


        String sql = "DELETE FROM account WHERE user_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, userId);

            if (stmt.executeUpdate() > 0) {
                System.out.println("Account deleted successfully.");
            } else {
                System.out.println("Delete failed: User ID " + userId + " not found.");
            }
        }
    }


    /**
     * Determines if the application is running in development mode based on system properties,
     * environment variables, or command-line arguments.
     *
     * @param args an array of command-line arguments
     * @return {@code true} if the application is in development mode; {@code false} otherwise
     */

    private static boolean isDevMode(String[] args) {
        if (Boolean.getBoolean("devMode"))
            return true;
        if ("true".equalsIgnoreCase(System.getenv("DEV_MODE")))
            return true;
        return Arrays.asList(args).contains("--dev");
    }

    /**
     * Reads configuration with precedence: Java system property first, then environment variable.
     * Returns trimmed value or null if neither source provides a non-empty value.
     */

    private static String resolveConfig(String propertyKey, String envKey) {
        String v = System.getProperty(propertyKey);
        if (v == null || v.trim().isEmpty()) {
            v = System.getenv(envKey);
        }
        return (v == null || v.trim().isEmpty()) ? null : v.trim();
    }


    }