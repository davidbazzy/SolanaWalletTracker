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
import java.util.logging.Level;
import java.util.logging.Logger;

public class Processor {

    private static final Logger logger = Logger.getLogger(Processor.class.getName());

    private final WalletService m_walletService;

    // Contains all tokens loaded from db
    private final ConcurrentHashMap<String, Token> m_tokenMap = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Wallet> m_wallets = new ConcurrentHashMap<>();

    private static final String m_systemExit = "0";

    private static final String m_holdings = "h";

    private static final String m_overlappingTokens = "o";

    private final Connection m_dbConnection;

    private static final Set<String> m_validCommands = Set.of(m_holdings, m_systemExit, m_overlappingTokens);

    private final MarketDataProcessor m_marketDataProcessor;

    // Better use over raw threads which are self-managed
    private final ScheduledExecutorService m_scheduler;

    // Single-thread executor used for sequential wallet loading (rate-limited)
    private final ExecutorService m_walletLoaderExecutor = Executors.newSingleThreadExecutor();
    private CompletableFuture<Void> m_walletsLoadFuture;

    // Timing constants
    private static final long MARKET_DATA_INTERVAL_SECONDS = 15L;
    private static final long POSITION_UPDATE_INTERVAL_SECONDS = 15L;
    private static final long WALLET_API_RATE_LIMIT_SECONDS = 4L;

    public Processor() {
        final HttpClient httpClient = HttpClient.newHttpClient();
        final ConcurrentHashMap<String, Token> sessionTokenMap = new ConcurrentHashMap<>();
        m_marketDataProcessor = new MarketDataProcessor(httpClient, sessionTokenMap);
        m_dbConnection = DatabaseConnUtil.getInstance().getDbConnection();
        SolanaRpcClient solanaRpc = SolanaRpcClient.createClient(SolanaNetwork.MAIN_NET.getEndpoint(), httpClient);
        m_walletService = new WalletService(solanaRpc, m_wallets, m_tokenMap, sessionTokenMap, m_dbConnection);
        m_scheduler = Executors.newScheduledThreadPool(2);
    }

    public void start(JTextArea outputArea) {
        loadWalletsAndTokensFromDb(outputArea);
        initiateMarketDataThread();
        initiatePositionUpdateThread();
    }

    public void stop() {
        // Attempt graceful shutdown of scheduled tasks
        try {
            logger.log(Level.INFO, "Shutting down scheduler...");
            m_scheduler.shutdownNow();
            if (!m_scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.log(Level.WARNING, "Interrupted while shutting down wallet loader executor", e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Exception while shutting down wallet loader executor", e);
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

    private void initiateMarketDataThread() {
        m_scheduler.scheduleAtFixedRate(() -> {
            try {
                m_marketDataProcessor.processMarketData();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Market Data Thread has thrown an Exception", e);
            }
        }, 0, MARKET_DATA_INTERVAL_SECONDS, TimeUnit.SECONDS);
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
                    Thread.sleep(WALLET_API_RATE_LIMIT_SECONDS);
                    logger.log(Level.INFO, "Sleeping for " + WALLET_API_RATE_LIMIT_SECONDS + "s due to sava API rate limits...");
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

    private void initiatePositionUpdateThread() {
        m_scheduler.scheduleAtFixedRate(() -> {
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

    // Ensure UI appends occur on the Swing EDT
    private void appendToOutput(JTextArea outputArea, String message) {
        if (SwingUtilities.isEventDispatchThread()) {
            outputArea.append(message);
        } else {
            SwingUtilities.invokeLater(() -> outputArea.append(message));
        }
    }

    // ...existing code...
}