package org.core.processors;

import org.core.accounts.Position;
import org.core.accounts.Token;
import org.core.prices.MarketData;
import org.core.utils.RestApiUtil;
import org.json.JSONObject;

import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MarketDataProcessor {

    private static final Logger logger = Logger.getLogger(MarketDataProcessor.class.getName());

    private final Map<String, MarketData> m_marketDataMap = new HashMap<>();

    private final HttpClient m_httpClient;

    private final Set<String> tokensWithNoMktData = new HashSet<>();

    public MarketDataProcessor(HttpClient httpClient) {
        m_httpClient = httpClient;
    }

    public void processMarketData(Map<String, Token> tokensMap) {
        logger.log(Level.INFO, "Size of tokens available = " + tokensMap.size());
        fetchMarketData(tokensMap);
    }

    public void fetchMarketData(Map<String, Token> tokensMap) {
        logger.log(Level.INFO, "Fetching market data...");

        String[] tokenIdArray = appendTokenIds(tokensMap, tokensWithNoMktData);

        if(tokenIdArray == null) {
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

            // Iterate over active tokens and update their prices
            for (String tokenMintAddress : tokensMap.keySet()) {
                if (data.has(tokenMintAddress)) {

                    if(data.isNull(tokenMintAddress)){
                        if (!tokensWithNoMktData.contains(tokenMintAddress)) {
                            // TODO: Tokens without a price from Jupiter are most likely spam coins. Analyze this at some point
                            logger.log(Level.WARNING, "Token price data is null for tokenMintAddress: " + tokenMintAddress);
                            tokensWithNoMktData.add(tokenMintAddress);
                        }
                        continue;
                    }

                    JSONObject tokenData = data.getJSONObject(tokenMintAddress);

                    double price = tokenData.getDouble("price");

                    // Check if MarketData already exists for token
                    if (m_marketDataMap.containsKey(tokenMintAddress)) {
                        MarketData marketData = m_marketDataMap.get(tokenMintAddress);
                        marketData.setUsdPrice(price);
                    } else {
                        MarketData marketData = new MarketData(tokenMintAddress, price);
                        m_marketDataMap.put(tokenMintAddress, marketData);
                        //tokensMap.get(tokenMintAddress).setMarketData(marketData);
                    }

                    // update tokensMap
                    if (tokensMap.get(tokenMintAddress).getMarketData() == null) {
                        tokensMap.get(tokenMintAddress).setMarketData(m_marketDataMap.get(tokenMintAddress));
                    }
                }
            }

            try {
                logger.log(Level.INFO, "Sleeping for 4 seconds to manage jupiter price rate limits");
                Thread.sleep(4000); // Sleep for 4 seconds to manage jupiter price rate limits
            } catch (InterruptedException e) {
                logger.log(Level.SEVERE, "Thread Sleep command for mkt data interrupted: " + e.getMessage());
            }
        }

        logger.log(Level.INFO, "Fetching market data complete!");
    }

    public void applyMarketData(Position position) {
        MarketData marketData = m_marketDataMap.get(position.getToken().getMintAddress());
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
    private String[] appendTokenIds(Map<String, Token> tokensMap, Set<String> tokensWithNoMktData) {

        try {
            if(tokensMap.isEmpty()) {
                return null;
            }

            int mktDataBatches = (int) Math.ceil((double) tokensMap.size() / 99);
            String[] tokenIdArray = new String[mktDataBatches];

            StringBuilder tokenIds = new StringBuilder();
            int batchCounter = 0;

            for (int i = 0; i < tokensMap.size(); i++) {
                String tokenMintAddress = (String) tokensMap.keySet().toArray()[i];

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
