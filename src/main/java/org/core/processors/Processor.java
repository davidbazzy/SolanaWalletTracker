package org.core.processors;

import org.apache.commons.lang3.tuple.Pair;
import org.core.accounts.Position;
import org.core.accounts.Token;
import org.core.accounts.Wallet;
import org.core.utils.DatabaseConnUtil;
import org.core.utils.UserInterfaceDisplayUtil;
import org.core.utils.ValidationUtil;
import org.core.utils.WalletService;
import software.sava.rpc.json.http.SolanaNetwork;
import software.sava.rpc.json.http.client.SolanaRpcClient;

import javax.swing.*;
import java.net.http.HttpClient;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Processor {

    private static final Logger logger = Logger.getLogger(Processor.class.getName());

    // In-memory wallet map (DB stored + fetched wallets onchain)
    private final ConcurrentHashMap<String, Wallet> m_wallets = new ConcurrentHashMap<>();

    // In-memory token map (includes db stored tokens + api fetched tokens)
    private final ConcurrentHashMap<String, Token> m_tokenMap = new ConcurrentHashMap<>();

    private final WalletService m_walletService;
    private final Connection m_dbConnection;
    private final MarketDataProcessor m_marketDataProcessor;

    // Better use over raw threads which are self-managed
    private final ScheduledExecutorService m_MarketDataAndPositionScheduler;

    // Single-thread executor used for sequential wallet loading (rate-limited)
    private final ExecutorService m_walletLoaderExecutor = Executors.newSingleThreadExecutor();
    private CompletableFuture<Void> m_walletsLoadFuture;

    // JavaFX callbacks
    private Runnable m_onWalletsLoaded;
    private Runnable m_onMarketDataUpdated;

    // Timing constants
    private static final int MARKET_DATA_INTERVAL_SECONDS = 15;
    private static final int POSITION_UPDATE_INTERVAL_SECONDS = 15;
    private static final int WALLET_API_RATE_LIMIT_SECONDS = 5;

    // Action constants
    private static final String m_systemExit = "0";
    private static final String m_holdings = "h";
    private static final String m_overlappingTokens = "o";
    private static final Set<String> m_validCommands = Set.of(m_holdings, m_systemExit, m_overlappingTokens);

    private Processor() {
        final HttpClient httpClient = HttpClient.newHttpClient();
        final ConcurrentHashMap<String, Token> sessionTokenMap = new ConcurrentHashMap<>();
        m_marketDataProcessor = new MarketDataProcessor(httpClient, sessionTokenMap);
        m_dbConnection = DatabaseConnUtil.getInstance().getDbConnection();
        SolanaRpcClient solanaRpc = SolanaRpcClient.createClient(SolanaNetwork.MAIN_NET.getEndpoint(), httpClient);
        m_walletService = new WalletService(solanaRpc, m_wallets, m_tokenMap, sessionTokenMap, m_dbConnection);
        m_MarketDataAndPositionScheduler = Executors.newScheduledThreadPool(2);
    }

    public static class SingletonProcessor {
        private static final Processor INSTANCE = new Processor();
    }

    public static Processor getInstance() {
        return SingletonProcessor.INSTANCE;
    }

    public void start(JTextArea outputArea) {
        loadWalletsAndTokensFromDb(outputArea);
        initiateMarketDataThread();
        initiatePositionUpdateThread();
    }

    /**
     * Start the processor with JavaFX callbacks (UI-agnostic version).
     * @param onWalletsLoaded Callback invoked when wallets are finished loading from DB
     * @param onMarketDataUpdated Callback invoked when market data is updated
     */
    public void startJavaFX(Runnable onWalletsLoaded, Runnable onMarketDataUpdated) {
        this.m_onWalletsLoaded = onWalletsLoaded;
        this.m_onMarketDataUpdated = onMarketDataUpdated;
        loadWalletsAndTokensFromDbJavaFX();
        initiateMarketDataThreadJavaFX();
        initiatePositionUpdateThreadJavaFX();
    }

    /**
     * Get the map of tracked wallets.
     */
    public Map<String, Wallet> getWallets() {
        return Collections.unmodifiableMap(m_wallets);
    }

    /**
     * Get the map of known tokens.
     */
    public Map<String, Token> getTokenMap() {
        return Collections.unmodifiableMap(m_tokenMap);
    }

    /**
     * Asynchronously add a wallet and invoke callback when complete.
     * @param name Wallet name/label
     * @param address Solana wallet address
     * @param callback Callback with the created Wallet (or null on failure)
     */
    public void addWalletAsync(String name, String address, Consumer<Wallet> callback) {
        CompletableFuture.runAsync(() -> {
            try {
                Pair<String, String> walletPair = Pair.of(name, address);
                m_walletService.processWalletForJavaFX(walletPair);
                Wallet wallet = m_wallets.get(address);
                callback.accept(wallet);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error adding wallet: " + address, e);
                callback.accept(null);
            }
        }, m_walletLoaderExecutor);
    }

    public void handleWalletAddressInput(JTextArea outputArea, String input) {
        if (input == null) {
            appendToOutput(outputArea, "❌ Invalid wallet input format. Please use: <wallet_name>:<wallet_address>\n");
            return;
        }

        input = input.trim();

        if (m_validCommands.contains(input)) {
            // Handle valid commands
            switch (input) {
                case m_systemExit:
                    appendToOutput(outputArea, "Shutting down gracefully\n");
                    stop();
                    try {
                        if (m_dbConnection != null && !m_dbConnection.isClosed()) {
                            m_dbConnection.close();
                        }
                    } catch (SQLException e) {
                        logger.log(Level.SEVERE, "Error closing DB during shutdown", e);
                    }
                    System.exit(0);
                    break;
                case m_holdings:
                    appendToOutput(outputArea, "Displaying holdings\n");
                    UserInterfaceDisplayUtil.displayWalletHoldingsCmd(outputArea, m_wallets);
                    break;
                case m_overlappingTokens:
                    appendToOutput(outputArea, "Displaying tokens present in more than one wallet\n");
                    UserInterfaceDisplayUtil.displayCommonTokensAcrossWalletsCmd(outputArea, m_wallets, m_tokenMap);
                    break;
                default:
                    appendToOutput(outputArea, "State valid commands: 0 (exit), h (show holdings), o (overlapping tokens)");
            }
        } else {
            Pair<String, String> walletAddressAndLabel = ValidationUtil.extractNameAndAddress(input);

            if (walletAddressAndLabel != null) {
                appendToOutput(outputArea, "Setting up wallet... please wait ⏳\n");
                m_walletService.processWalletForCmd(outputArea, walletAddressAndLabel);
            } else {
                appendToOutput(outputArea, "❌ Invalid wallet input format. Please use: <wallet_name>:<wallet_address>\n");
            }
        }
    }

    /**
     * Market data thread defined with a sleep interval to respect Jupiter API limits
     */
    private void initiateMarketDataThread() {
        m_MarketDataAndPositionScheduler.scheduleAtFixedRate(() -> {
            try {
                m_marketDataProcessor.processMarketData();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Market Data Thread has thrown an Exception", e);
            }
        }, 0, MARKET_DATA_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    // TODO: Position update thread to be applied periodically. Update to make this event driven based on mkt data changes for a given position
    private void initiatePositionUpdateThread() {
        m_MarketDataAndPositionScheduler.scheduleAtFixedRate(() -> {
            try {
                logger.log(Level.INFO, "Updating positions...");
                for (Wallet wallet : m_wallets.values()) {
                    for (Position position : wallet.getPositions().values()) {
                        m_marketDataProcessor.applyMarketDataToPosition(position);
                    }
                }
                logger.log(Level.INFO, "Positions updated!");
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Positions Update Thread has thrown an Exception", e);
            }
        }, 0, POSITION_UPDATE_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * JavaFX version: Market data thread with callback notification
     */
    private void initiateMarketDataThreadJavaFX() {
        m_MarketDataAndPositionScheduler.scheduleAtFixedRate(() -> {
            try {
                m_marketDataProcessor.processMarketData();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Market Data Thread has thrown an Exception", e);
            }
        }, 0, MARKET_DATA_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * JavaFX version: Position update thread with callback notification
     */
    private void initiatePositionUpdateThreadJavaFX() {
        m_MarketDataAndPositionScheduler.scheduleAtFixedRate(() -> {
            try {
                logger.log(Level.INFO, "Updating positions...");
                for (Wallet wallet : m_wallets.values()) {
                    for (Position position : wallet.getPositions().values()) {
                        m_marketDataProcessor.applyMarketDataToPosition(position);
                    }
                }
                logger.log(Level.INFO, "Positions updated!");
                // Notify JavaFX UI of market data update
                if (m_onMarketDataUpdated != null) {
                    m_onMarketDataUpdated.run();
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Positions Update Thread has thrown an Exception", e);
            }
        }, 0, POSITION_UPDATE_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * JavaFX version: Load wallets without JTextArea dependency
     */
    private void loadWalletsAndTokensFromDbJavaFX() {
        DatabaseConnUtil.loadTokensFromDb(m_dbConnection, m_tokenMap);

        Set<Pair<String, String>> walletAddresses = DatabaseConnUtil.loadWalletsFromDb(m_dbConnection);

        m_walletsLoadFuture = CompletableFuture.runAsync(() -> {
            for (Pair<String, String> walletAddress : walletAddresses) {
                try {
                    m_walletService.processWalletForJavaFX(walletAddress);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Exception while processing stored wallet: " + walletAddress, e);
                }

                try {
                    logger.log(Level.INFO, "Sleeping for " + WALLET_API_RATE_LIMIT_SECONDS + "s due to sava API rate limits...");
                    Thread.sleep(WALLET_API_RATE_LIMIT_SECONDS * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.log(Level.WARNING, "Wallet loading thread interrupted while sleeping", e);
                    break;
                }
            }
        }, m_walletLoaderExecutor).whenComplete((v, ex) -> {
            if (ex != null && !(ex instanceof CancellationException)) {
                logger.log(Level.SEVERE, "Error while loading wallets", ex);
            } else {
                logger.log(Level.INFO, "Finished loading wallets");
                // Notify JavaFX UI that wallets are loaded
                if (m_onWalletsLoaded != null) {
                    m_onWalletsLoaded.run();
                }
            }
        });
    }

    private void loadWalletsAndTokensFromDb(JTextArea outputArea) {
        DatabaseConnUtil.loadTokensFromDb(m_dbConnection, m_tokenMap);

        // Fetch list of wallet names
        Set<Pair<String,String>> walletAddresses = DatabaseConnUtil.loadWalletsFromDb(m_dbConnection);

        // Run sequential wallet processing on a dedicated single-thread executor to respect rate limits
        m_walletsLoadFuture = CompletableFuture.runAsync(() -> {
            for (Pair<String,String> walletAddress : walletAddresses) {
                try {
                    m_walletService.processWalletForCmd(outputArea, walletAddress);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Exception while processing stored wallet: " + walletAddress, e);
                }

                try {
                    logger.log(Level.INFO, "Sleeping for " + WALLET_API_RATE_LIMIT_SECONDS + "s due to sava API rate limits...");
                    Thread.sleep(WALLET_API_RATE_LIMIT_SECONDS * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.log(Level.WARNING, "Wallet loading thread interrupted while sleeping", e);
                }
            }
        }, m_walletLoaderExecutor).whenComplete((v, ex) -> {
            if (ex != null && !(ex instanceof CancellationException)) {
                logger.log(Level.SEVERE, "Error while loading wallets ", ex);
            } else {
                logger.log(Level.INFO, "Finished loading wallets");
            }
        });
    }

    public void stop() {
        // Attempt graceful shutdown of scheduled tasks
        try {
            logger.log(Level.INFO, "Shutting down scheduler...");
            m_MarketDataAndPositionScheduler.shutdownNow();
            if (!m_MarketDataAndPositionScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.log(Level.WARNING, "Scheduler did not terminate within timeout.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.log(Level.WARNING, "Interrupted while shutting down scheduler", e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Exception while shutting down scheduler", e);
        }

        // Attempt to stop wallet loader and cancel outstanding future
        try {
            if (m_walletsLoadFuture != null) {
                m_walletsLoadFuture.cancel(true);
            }
            m_walletLoaderExecutor.shutdownNow();
            if (!m_walletLoaderExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                logger.log(Level.WARNING, "Wallet loader executor did not terminate within timeout.");
            }

            m_walletService.shutdown();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.log(Level.WARNING, "Interrupted while shutting down wallet loader and/or thread executor", e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Exception while shutting down wallet loader and/or thread executor", e);
        }

        // Close DB connection
        try {
            if (m_dbConnection != null && !m_dbConnection.isClosed()) {
                m_dbConnection.close();
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to close DB connection", e);
        }
    }

    // Ensure UI appends occur on the Swing EDT
    private void appendToOutput(JTextArea outputArea, String message) {
        if (SwingUtilities.isEventDispatchThread()) {
            outputArea.append(message);
        } else {
            SwingUtilities.invokeLater(() -> outputArea.append(message));
        }
    }

}