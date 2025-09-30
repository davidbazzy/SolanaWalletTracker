package org.core.processors;

import javafx.scene.control.TextArea;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Processor {

    private static final Logger logger = Logger.getLogger(Processor.class.getName());

    private final SolanaRpcClient m_solanaRpc;

    private final WalletService m_walletService;

    // Contains all tokens loaded from db
    private final ConcurrentHashMap<String, Token> m_tokenMap = new ConcurrentHashMap<>();

    // Contains tokens relevant for current session only
    private final ConcurrentHashMap<String, Token> m_sessionTokenMap = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Wallet> m_wallets = new ConcurrentHashMap<>();

    private static final String m_systemExit = "0";

    private static final String m_holdings = "h";

    private static final String m_overlappingTokens = "o";

    private Connection m_dbConnection;

    private static final Set<String> m_validCommands = Set.of(m_holdings, m_systemExit, m_overlappingTokens);

    private final MarketDataProcessor m_marketDataProcessor;

    // Better use over raw threads which are self-managed
    private final ScheduledExecutorService m_scheduler;

    public Processor() {
        final HttpClient httpClient = HttpClient.newHttpClient();
        m_solanaRpc = SolanaRpcClient.createClient(SolanaNetwork.MAIN_NET.getEndpoint(), httpClient);
        m_marketDataProcessor = new MarketDataProcessor(httpClient);
        m_dbConnection = DatabaseConnUtil.initiateDbConnection();
        DatabaseConnUtil.loadTokensFromDb(m_dbConnection, m_tokenMap);
        // TODO: Load stored wallets from db here and call process wallet logic?
        m_walletService = new WalletService(m_solanaRpc, m_wallets, m_tokenMap, m_sessionTokenMap, m_dbConnection);
        m_scheduler = Executors.newScheduledThreadPool(2);
    }

    public void start() {
        initiateMarketDataThread();
        initiatePositionUpdateThread();
    }

    public void stop() {
        m_scheduler.shutdownNow();
    }

    public void handleWalletAddressInput(JTextArea outputArea, String input) {
        if (m_validCommands.contains(input)) {
            // Handle valid commands
            switch (input) {
                case m_systemExit:
                    outputArea.append("Shutting down gracefully\n");
                    stop();

                    try {
                        m_dbConnection.close();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }

                    System.exit(0);
                    break;
                case m_holdings:
                    outputArea.append("Displaying holdings\n");
                    UserInterfaceDisplayUtil.displayWalletHoldingsToDelete(outputArea, m_wallets);
                    break;
                case m_overlappingTokens:
                    outputArea.append("Displaying tokens present in more than one wallet\n");
                    UserInterfaceDisplayUtil.displayCommonTokensAcrossWalletsCmd(outputArea, m_wallets, m_tokenMap);
                    break;
            }
        } else {
            Pair<String, String> walletAddressAndLabel = ValidationUtil.extractNameAndAddress(input);

            if (walletAddressAndLabel != null) {
                m_walletService.processWalletForCmd(outputArea, walletAddressAndLabel);
            } else {
                outputArea.append("❌ Invalid wallet input format. Please use: <wallet_name>:<wallet_address>\n");
            }
        }
    }

    private void initiateMarketDataThread() {
        m_scheduler.scheduleAtFixedRate(() -> {
            try {
                logger.log(Level.INFO, "Fetching market data...");
                m_marketDataProcessor.processMarketData(m_sessionTokenMap);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Market Data Thread has thrown an Exception", e);
            }
        }, 0, 15, TimeUnit.SECONDS);
    }

    private void initiatePositionUpdateThread() {
        m_scheduler.scheduleAtFixedRate(() -> {
            try {
                logger.log(Level.INFO, "Updating positions...");
                for (Wallet wallet : m_wallets.values()) {
                    for (Position position : wallet.getPositions().values()) {
                        m_marketDataProcessor.applyMarketData(position);
                    }
                }
                logger.log(Level.INFO, "Positions updated!");
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Positions Update Thread has thrown an Exception", e);
            }
        }, 0, 15, TimeUnit.SECONDS);
    }

}