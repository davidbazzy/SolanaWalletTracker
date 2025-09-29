package org.core.processors;

import javafx.scene.control.TextArea;
import org.core.accounts.Token;
import org.core.accounts.Wallet;
import org.core.utils.DatabaseConnUtil;
import org.core.utils.UserInterfaceDisplayUtil;
import org.core.utils.ValidationUtil;
import org.core.utils.WalletService;
import software.sava.rpc.json.http.SolanaNetwork;
import software.sava.rpc.json.http.client.SolanaRpcClient;

import java.net.http.HttpClient;
import java.sql.Connection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProcessorJavaFX {

    // TODO: To work on JavaFX implementation once cmd implementation is complete & efficient
    /*private static final Logger logger = Logger.getLogger(Processor.class.getName());

    private final SolanaRpcClient m_solanaRpc;

    private final WalletService m_walletService;

    private final ConcurrentHashMap<String, Token> m_tokenMap = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Token> m_sessionTokenMap = new ConcurrentHashMap<>();

    private final Map<String, Wallet> m_wallets = new ConcurrentHashMap<>();

    private static final String m_systemExit = "0";

    private static final String m_holdings = "h";

    private static final String m_overlappingTokens = "o";

    private Connection m_dbConnection;

    private static final Set<String> m_validCommands = Set.of(m_holdings, m_systemExit, m_overlappingTokens);

    private final MarketDataProcessor m_marketDataProcessor;

    public ProcessorJavaFX() {
        final HttpClient httpClient = HttpClient.newHttpClient();
        m_solanaRpc = SolanaRpcClient.createClient(SolanaNetwork.MAIN_NET.getEndpoint(), httpClient);
        m_marketDataProcessor = new MarketDataProcessor(httpClient);
        m_dbConnection = DatabaseConnUtil.initiateDbConnection();
        // Initate Mkt data and pos update threads - once java FX class is ready
        //initiateMarketDataThread();
        //initiatePositionUpdateThread();
        DatabaseConnUtil.loadTokensFromDb(m_dbConnection, m_tokenMap);
        m_walletService = new WalletService(m_solanaRpc, m_wallets, m_tokenMap,  m_dbConnection);
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

            Wallet wallet = m_walletService.processWalletForJavaFx(input);
            outputArea.appendText(String.format("✅ Wallet contents retrieved for: %s \n", input));

            if (wallet != null) {
                outputArea.appendText(String.format("🔃 Active positions: %s ", wallet.getPositions().size()));
            } else {
                outputArea.appendText("❌ Failed to retrieve wallet contents\n");
            }

        } else {
            outputArea.appendText("❌ Invalid wallet address \n");
        }
    }*/
}
