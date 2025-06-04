package org.core.processors;

import javafx.scene.control.TextArea;
import org.core.accounts.Position;
import org.core.accounts.Token;
import org.core.accounts.Wallet;
import org.core.utils.DatabaseConnUtil;
import org.core.utils.UserInterfaceDisplayUtil;
import org.core.utils.ValidationUtil;
import org.core.utils.SolanaWalletUtil;
import software.sava.rpc.json.http.SolanaNetwork;
import software.sava.rpc.json.http.client.SolanaRpcClient;

import javax.swing.*;
import java.net.http.HttpClient;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Processor {

    private static final Logger logger = Logger.getLogger(Processor.class.getName());

    private final SolanaRpcClient m_solanaRpc;

    private final Map<String, Token> m_tokenMap = new ConcurrentHashMap<>();

    private final Map<String, Token> m_activetokenMap = new ConcurrentHashMap<>();

    private final Map<String, Wallet> m_wallets = new HashMap<>();

    private static final String m_systemExit = "0";

    private static final String m_holdings = "h";

    private static final String m_overlappingTokens = "o";

    private Connection m_dbConnection;

    private static final Set<String> m_validCommands = Set.of(m_holdings, m_systemExit, m_overlappingTokens);

    private final MarketDataProcessor m_marketDataProcessor;

    public Processor() {
        final HttpClient httpClient = HttpClient.newHttpClient();
        m_solanaRpc = SolanaRpcClient.createClient(SolanaNetwork.MAIN_NET.getEndpoint(), httpClient);
        m_marketDataProcessor = new MarketDataProcessor(httpClient);
        m_dbConnection = DatabaseConnUtil.initiateDbConnection();
        initiateMarketDataThread();
        initiatePositionUpdateThread();
        DatabaseConnUtil.loadTokensFromDb(m_dbConnection, m_tokenMap);
    }

    public void handleExit() {
        logger.log(Level.INFO, "Shutting down gracefully");
        System.exit(0);
    }

    public void handleHoldings(TextArea outputArea) {
        logger.log(Level.INFO, "Displaying holdings");
        UserInterfaceDisplayUtil.displayWalletHoldings(outputArea, m_wallets);
    }

    public void handleSharedTokens(TextArea outputArea) {
        UserInterfaceDisplayUtil.displayCommonTokensAcrossWallets(outputArea, m_wallets);
        //JSONObject json = JupiterApiUtil.getTokenDetailsFromSolanaFm();
        // Implement the logic to handle shared tokens and display in outputArea
    }

    public void handleWalletAddressInput(TextArea outputArea, String input) {
        if (ValidationUtil.validateAddressFormat(input)) {
            logger.log(Level.INFO, "Valid Wallet address format: " + input);

            Wallet wallet = SolanaWalletUtil.processWallet(m_solanaRpc, m_wallets, input, m_tokenMap, m_activetokenMap, m_dbConnection);
            outputArea.appendText(String.format("✅ Wallet contents retrieved for: %s \n", input));

            if (wallet != null) {
                outputArea.appendText(String.format("🔃 Active positions: %s ", wallet.getPositions().size()));
            } else {
                outputArea.appendText("❌ Failed to retrieve wallet contents\n");
            }

        } else {
            outputArea.appendText("❌ Invalid wallet address \n");
        }
    }

    public void handleWalletAddressInput(JTextArea outputArea, String input) {
        if (m_validCommands.contains(input)) {
            // Handle valid commands
            switch (input) {
                case m_systemExit:
                    outputArea.append("Shutting down gracefully\n");

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
        } else if (ValidationUtil.validateAddressFormat(input)) {
            logger.log(Level.INFO, "Valid Wallet address format: " + input);

            SolanaWalletUtil.processWalletForCmd(outputArea, m_solanaRpc, m_wallets, input, m_tokenMap,  m_activetokenMap, m_dbConnection);
        } else {
            outputArea.append("Invalid input\n");
        }
    }

    private void initiateMarketDataThread() {
        // initiate market data thread
        Thread marketDataThread = new Thread(() -> {
            while(true) {
                logger.log(Level.INFO,"Fetching market data...");
                m_marketDataProcessor.processMarketData(m_tokenMap);
                try {
                    Thread.sleep(15000);
                } catch (InterruptedException e) {
                    logger.log(Level.SEVERE, "Market Data Thread has thrown an Exception", e);
                }
            }
        }, "MarketDataThread");

        marketDataThread.start();
    }

    // In Processor.java
    private void initiatePositionUpdateThread() {
        Thread positionUpdateThread = new Thread(() -> {
            while (true) {
                logger.log(Level.INFO, "Updating positions...");
                for (Wallet wallet : m_wallets.values()) {
                    for (Position position : wallet.getPositions().values()) {
                        m_marketDataProcessor.applyMarketData(position);
                    }
                }
                logger.log(Level.INFO, "Positions updated!");
                try {
                    Thread.sleep(15000); // Update every 15 seconds
                } catch (InterruptedException e) {
                    logger.log(Level.SEVERE, "Positions Update Thread has thrown an Exception", e);
                }
            }
        }, "PositionUpdateThread");
        positionUpdateThread.start();
    }

}