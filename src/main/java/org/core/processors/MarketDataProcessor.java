package org.core.processors;

import org.core.accounts.Position;
import org.core.accounts.Token;
import org.core.prices.MarketData;
import org.core.utils.RestApiUtil;
import org.json.JSONObject;

import java.net.http.HttpClient;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MarketDataProcessor {

    private static final Logger logger = Logger.getLogger(MarketDataProcessor.class.getName());

    private final HttpClient m_httpClient;

    private final Set<String> tokensWithNoMktData = new HashSet<>();

    public MarketDataProcessor(HttpClient httpClient) {
        m_httpClient = httpClient;
    }

    public void processMarketData(Map<String, Token> sessionTokenMap) {
        logger.log(Level.INFO, "Size of tokens available = " + sessionTokenMap.size());
        fetchMarketData(sessionTokenMap);
    }

    public void fetchMarketData(Map<String, Token> sessionTokenMap) {
        logger.log(Level.INFO, "Fetching market data...");

        String[] tokenIdArray = appendTokenIds(sessionTokenMap, tokensWithNoMktData);

        if (tokenIdArray == null) {
            logger.log(Level.WARNING, "No token IDs found to fetch market data");
            return;
        }

        for (int i = 0; i < tokenIdArray.length; i++) {
            logger.log(Level.INFO, "Fetching market data for token batch: " + (i + 1) + " of " + tokenIdArray.length);
            JSONObject data = RestApiUtil.getMarketDataForTokens(m_httpClient, tokenIdArray[i]);

            if (data == null) {
                logger.log(Level.WARNING, "Failed to fetch market data as data from Jupiter API is null");
                return;
            }

            Token token;
            String tokenMintAddress;

            // Iterate over active tokens and update their prices
            for (Map.Entry<String, Token> tokenEntry : sessionTokenMap.entrySet()) {
                tokenMintAddress = tokenEntry.getKey();
                token = tokenEntry.getValue();

                if (data.has(tokenMintAddress)) {
                    if(data.isNull(tokenMintAddress)){
                        if (!tokensWithNoMktData.contains(tokenMintAddress)) {
                            // TODO: Tokens without a price from Jupiter are most likely spam coins. Create a blacklist table for these tokens in db
                            logger.log(Level.WARNING, "Token price data is null for tokenMintAddress: " + tokenMintAddress);
                            tokensWithNoMktData.add(tokenMintAddress);
                        }
                        continue;
                    }

                    JSONObject tokenData = data.getJSONObject(tokenMintAddress);

                    double price = tokenData.getDouble("price");
                    MarketData existingMarketData = token.getMarketData();

                    // Check if market data already exists for token
                    if (existingMarketData != null) {
                        existingMarketData.setUsdPrice(price);
                    } else {
                        MarketData marketData = new MarketData(tokenMintAddress, price);
                        token.setMarketData(marketData);
                    }
                }
            }

            try {
                logger.log(Level.INFO, "Sleeping for 4 seconds to manage jupiter price rate limits");
                Thread.sleep(4000);
            } catch (InterruptedException e) {
                logger.log(Level.SEVERE, "Thread Sleep command for mkt data interrupted: " + e.getMessage());
            }
        }

        logger.log(Level.INFO, "Fetching market data complete!");
    }

    public void applyMarketData(Position position) {
        MarketData marketData = position.getToken().getMarketData();
        if (marketData != null) {
            double usdPrice = marketData.getUsdPrice();
            double usdBalance = position.getTokenBalance() * usdPrice;
            position.setUsdBalance(usdBalance);
            position.getToken().setMarketData(marketData);
        }
    }

    /**
        Jupiter API has a limit of 100 tokens per request.
        To avoid hitting the rate limit, we need to split the token IDs into chunks of 100.
     */
    private String[] appendTokenIds(Map<String, Token> sessionTokenMap, Set<String> tokensWithNoMktData) {

        try {
            if (sessionTokenMap.isEmpty()) {
                return null;
            }

            int mktDataBatches = (int) Math.ceil((double) sessionTokenMap.size() / 99);
            String[] tokenIdArray = new String[mktDataBatches];

            StringBuilder tokenIds = new StringBuilder();
            int batchCounter = 0;

            for (int i = 0; i < sessionTokenMap.size(); i++) {
                String tokenMintAddress = (String) sessionTokenMap.keySet().toArray()[i];

                if (i != 0 && i % 99 == 0 ) {
                    tokenIdArray[batchCounter] = tokenIds.toString();
                    batchCounter++;
                    tokenIds = new StringBuilder();
                }

                if (!tokensWithNoMktData.contains(tokenMintAddress)) {
                    tokenIds.append(tokenMintAddress).append(",");
                }
            }

            tokenIdArray[batchCounter] = tokenIds.toString();

            return tokenIdArray;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error while appending token IDs: " + e.getMessage());
        }

        return null;
    }

}
