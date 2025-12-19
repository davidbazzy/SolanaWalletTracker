package org.core.processors;

import org.apache.commons.lang3.tuple.Pair;
import org.core.accounts.Position;
import org.core.accounts.Token;
import org.core.accounts.Wallet;
import org.core.utils.DatabaseConnUtil;
import org.core.utils.WalletService;
import software.sava.rpc.json.http.SolanaNetwork;
import software.sava.rpc.json.http.client.SolanaRpcClient;

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

    // In-memory copy of blacklisted tokens
    private final CopyOnWriteArraySet<String> m_blacklistedTokens = new CopyOnWriteArraySet<>();

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
    private Consumer<Wallet> m_onWalletLoaded;

    // Timing constants
    private static final int MARKET_DATA_INTERVAL_SECONDS = 15;
    private static final int POSITION_UPDATE_INTERVAL_SECONDS = 15;
    private static final int WALLET_API_RATE_LIMIT_SECONDS = 5;

    private Processor() {
        final HttpClient httpClient = HttpClient.newHttpClient();
        final ConcurrentHashMap<String, Token> sessionTokenMap = new ConcurrentHashMap<>();
        m_dbConnection = DatabaseConnUtil.getInstance().getDbConnection();
        m_marketDataProcessor = new MarketDataProcessor(httpClient,m_dbConnection, sessionTokenMap, m_blacklistedTokens);
        SolanaRpcClient solanaRpc = SolanaRpcClient.createClient(SolanaNetwork.MAIN_NET.getEndpoint(), httpClient);
        m_walletService = new WalletService(solanaRpc, m_wallets, m_tokenMap, sessionTokenMap, m_dbConnection, m_blacklistedTokens);
        m_MarketDataAndPositionScheduler = Executors.newScheduledThreadPool(2);
    }

    public static class SingletonProcessor {
        private static final Processor INSTANCE = new Processor();
    }

    public static Processor getInstance() {
        return SingletonProcessor.INSTANCE;
    }

    /**
     * Start the processor with JavaFX callbacks (UI-agnostic version).
     * @param onWalletLoaded Callback invoked each time a wallet is loaded
     * @param onWalletsLoaded Callback invoked when all wallets are finished loading from DB
     * @param onMarketDataUpdated Callback invoked when market data is updated
     */
    public void startJavaFX(Consumer<Wallet> onWalletLoaded, Runnable onWalletsLoaded, Runnable onMarketDataUpdated) {
        this.m_onWalletLoaded = onWalletLoaded;
        this.m_onWalletsLoaded = onWalletsLoaded;
        this.m_onMarketDataUpdated = onMarketDataUpdated;
        DatabaseConnUtil.loadBlacklistedTokensFromDb(m_dbConnection, m_blacklistedTokens);
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
     * Load wallets and complete token list from DB. Fetch wallet content asynchronously using CompletableFutures
     */
    private void loadWalletsAndTokensFromDbJavaFX() {
        long startTime = System.nanoTime();
        DatabaseConnUtil.loadTokensFromDb(m_dbConnection, m_tokenMap);
        Set<Pair<String, String>> walletAddresses = DatabaseConnUtil.loadWalletsFromDb(m_dbConnection);

        m_walletsLoadFuture = CompletableFuture.runAsync(() -> {
            for (Pair<String, String> walletAddress : walletAddresses) {
                try {
                    m_walletService.processWalletForJavaFX(walletAddress);
                    // Notify UI that this wallet has been loaded
                    Wallet wallet = m_wallets.get(walletAddress.getRight());
                    if (wallet != null && m_onWalletLoaded != null) {
                        m_onWalletLoaded.accept(wallet);
                    }
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

        long endTime = System.nanoTime();
        double duration = (double) (endTime - startTime) / 1000000000; // Duration in seconds
        logger.log(Level.INFO,String.format( "Startup Wallet & Token load (from DB & Sava RPC APIs took %f seconds", duration));
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
}