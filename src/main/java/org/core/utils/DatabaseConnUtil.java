package org.core.utils;

import org.core.accounts.Token;
import org.core.accounts.Wallet;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseConnUtil {

    private static final Logger logger = Logger.getLogger(DatabaseConnUtil.class.getName());

    public static String getDbPassword() throws IOException {
        Properties props = new Properties();
        try (InputStream input = DatabaseConnUtil.class.getClassLoader().getResourceAsStream("secrets.properties")) {
            props.load(input);
        }
        return props.getProperty("db.password");
    }

    public static Connection initiateDbConnection() {
        String jdbcUrl = "jdbc:postgresql://localhost:5432/postgres";
        String username = "postgres";
        String password = "";

        try {
            password = getDbPassword();
            return DriverManager.getConnection(jdbcUrl, username, password);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to connect to the database", e);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to load database password from properties file", e);
        }

        return null;
    }

    public static void persistTokenToDb(Connection connection, Token token) {

        if (token == null || "Unknown Token".equals(token.getName())) {
            System.out.println("Token is null or has an unknown name. Skipping persistence.");
            return;
        }

        String sql = "INSERT INTO token (mint_address, name, ticker, decimals, date_added) VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, token.getMintAddress());
            stmt.setString(2, token.getName());
            stmt.setString(3, token.getTicker());
            stmt.setInt(4, token.getDecimals());
            stmt.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to persist token to database", e);
        }
    }

    public static void persistWalletToDb(Connection connection, Wallet wallet) {
        String sql = """
                       INSERT INTO wallet (wallet_address, wallet_name, sol_balance, date_added, date_updated)
                        VALUES (?, ?, ?, ?, ?) ON CONFLICT (wallet_address) DO UPDATE SET sol_balance = ?, date_updated = ?
                """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, wallet.getAddress());
            stmt.setString(2, wallet.getName());
            stmt.setDouble(3, wallet.getSolBalance());
            stmt.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            stmt.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
            stmt.setDouble(6, wallet.getSolBalance());
            stmt.setTimestamp(7, new Timestamp(System.currentTimeMillis()));
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to persist wallet to database", e);
        }
    }

    public static void persistPositionToDb(Connection connection, String walletAddress, String positionAccountAddress, String tokenMintAddress, String tokenTicker, double balance) {
        String sql = """
                       INSERT INTO position (wallet_address, position_account_address, token_mint_address, 
                       token_ticker, token_balance, date_added, date_updated) 
                       VALUES (?, ?, ?, ?, ?, ?, ?) 
                """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, walletAddress);
            stmt.setString(2, positionAccountAddress);
            stmt.setString(3, tokenMintAddress);
            stmt.setString(4, tokenTicker);
            stmt.setDouble(5, balance);
            stmt.setTimestamp(6, new Timestamp(System.currentTimeMillis()));
            stmt.setTimestamp(7, new Timestamp(System.currentTimeMillis()));
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to persist position to database", e);
        }
    }

    // TODO: Amend method to only load Tokens which are part of the WALLETs that have been stored in the DB
    public static void loadTokensFromDb(Connection connection, Map<String, Token> tokensMap) {
        String sql = "SELECT * FROM token";

        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {

            while (resultSet.next()) {
                String mintAddress = resultSet.getString("mint_address");
                String name = resultSet.getString("name");
                String ticker = resultSet.getString("ticker");
                int decimals = resultSet.getInt("decimals");

                // Create a Token object and add it to your map or list
                Token token = new Token(mintAddress, name, ticker, decimals);
                // Add token to your map or list
                tokensMap.put(mintAddress, token);
            }

            logger.log(Level.INFO, "Tokens loaded from database: " + tokensMap.size());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
