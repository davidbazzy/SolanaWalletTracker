package org.core.utils;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.core.accounts.Token;
import org.core.accounts.Wallet;

import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseConnUtil {

    private static final Logger logger = Logger.getLogger(DatabaseConnUtil.class.getName());

    private final Connection m_dbConnection;

    private DatabaseConnUtil() {
        m_dbConnection = initiateDbConnection();
    }

    private static class SingletonHolder {
        private static final DatabaseConnUtil INSTANCE = new DatabaseConnUtil();
    }

    public static DatabaseConnUtil getInstance() {
        return SingletonHolder.INSTANCE;
    }

    public Connection getDbConnection() {
        return m_dbConnection;
    }

    private static String getDbPassword() throws IOException {
        Properties props = new Properties();
        try (InputStream input = DatabaseConnUtil.class.getClassLoader().getResourceAsStream("secrets.properties")) {
            props.load(input);
        }
        return props.getProperty("db.password");
    }

    private static Connection initiateDbConnection() {
        String jdbcUrl = "jdbc:postgresql://localhost:5432/postgres";
        String username = "postgres";

        try {
            String password = getDbPassword();
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
            logger.log(Level.WARNING, "Token %s is null or has an unknown name. Skipping persistence.");
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

    // TODO: Review storing positions in DB... might not be worth it. Might be worth just storing wallet addresses
    // and upon every start up, active positions for these wallets
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

    public static Set<Pair<String,String>> loadWalletsFromDb(Connection connection) {
        HashSet<Pair<String,String>> wallets = new HashSet<>();
        String sql = "SELECT * FROM wallet";

        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                String name = resultSet.getString("wallet_name");
                String address = resultSet.getString("wallet_address");
                Pair<String,String> walletDetails = new ImmutablePair<>(name,address);
                wallets.add(walletDetails);
            }
        } catch (SQLException e) {
            logger.log(Level.INFO, "Error loading wallet from database");
        }

        return wallets;
    }

    public static void loadTokensFromDb(Connection connection, ConcurrentHashMap<String, Token> tokensMap) {
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
            logger.log(Level.INFO, "Error loading tokens from database: " + tokensMap.size());
        }
    }
}
