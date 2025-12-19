package org.core.processors;

import org.core.accounts.Position;
import org.core.accounts.Token;
import org.core.prices.MarketData;
import org.core.utils.DatabaseConnUtil;
import org.core.utils.RestApiUtil;
import org.json.JSONObject;

import java.net.http.HttpClient;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MarketDataProcessor {

    private static final Logger logger = Logger.getLogger(MarketDataProcessor.class.getName());

    private static final int s_jupTokenLimitRequest = 50;

    private final HttpClient m_httpClient;
    private final ConcurrentHashMap<String, Token> m_sessionTokenMap;
    private final CopyOnWriteArraySet<String> m_blacklistedTokens;
    private final Connection m_dbConnection;

    public MarketDataProcessor(HttpClient httpClient, Connection dbConnection, ConcurrentHashMap<String, Token> sessionTokenMap,
                               CopyOnWriteArraySet<String> blacklistedTokens) {
        m_httpClient = httpClient;
        m_sessionTokenMap = sessionTokenMap;
        m_dbConnection = dbConnection;
        m_blacklistedTokens = blacklistedTokens;
    }

    public void processMarketData() {
        logger.log(Level.INFO, "Fetching market data for token size: " + m_sessionTokenMap.size());

        if (m_sessionTokenMap.isEmpty()) {
            logger.log(Level.WARNING, "No token IDs found to fetch market data");
            return;
        }

        String[] tokenIdArray = appendTokenIds(m_sessionTokenMap, m_blacklistedTokens);

        if (tokenIdArray == null) return;

        for (int i = 0; i < tokenIdArray.length; i++) {
            logger.log(Level.INFO, "Fetching market data for token batch: " + (i + 1) + " of " + tokenIdArray.length);
            JSONObject data = RestApiUtil.getMarketDataForTokens(m_httpClient, tokenIdArray[i]);

            if (data.isEmpty()) {
                logger.log(Level.WARNING, "Market Data response for last set of tokens is empty");
                return;
            }

            // Iterate over tokens just queried for mkt data and update market data object
            for (String tokenMintAddress : tokenIdArray[i].split(",")) {
                Token token = m_sessionTokenMap.get(tokenMintAddress);
                if (data.has(tokenMintAddress)) {
                    if(data.isNull(tokenMintAddress)){
                        if (!m_blacklistedTokens.contains(tokenMintAddress)) {
                            // TODO: Tokens without a price from Jupiter are most likely spam coins. Create a blacklist table for these tokens in db (Need to diff between good tokens not havent mkt data on a rare occassion)
                            logger.log(Level.WARNING, "Token price data is null for tokenMintAddress: " + tokenMintAddress);
                            m_blacklistedTokens.add(tokenMintAddress);
                            DatabaseConnUtil.persistBlacklistedTokenToDb(m_dbConnection, token, m_blacklistedTokens);
                        }
                        continue;
                    }

                    JSONObject tokenData = data.getJSONObject(tokenMintAddress);
                    double price = tokenData.getDouble("usdPrice");
                    MarketData existingMarketData = token.getMarketData();

                    // Check if market data already exists for token
                    if (existingMarketData != null) {
                        if (existingMarketData.getUsdPrice() != price) existingMarketData.setUsdPrice(price);
                    } else {
                        logger.log(Level.INFO, String.format("Market data received from Jupiter for token: %s - %s ", tokenMintAddress, token.getTicker()));
                        MarketData marketData = new MarketData(tokenMintAddress, price);
                        token.setMarketData(marketData);
                    }
                } else {
                    if (!m_blacklistedTokens.contains(tokenMintAddress)) {
                        logger.log(Level.WARNING, String.format("No market data response found for token: %s - %s ", tokenMintAddress, token.getTicker()));
                        m_blacklistedTokens.add(tokenMintAddress);
                        DatabaseConnUtil.persistBlacklistedTokenToDb(m_dbConnection, token, m_blacklistedTokens);
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

    public void applyMarketDataToPosition(Position position) {
        MarketData marketData = position.getToken().getMarketData();
        if (marketData != null) {
            double usdPrice = marketData.getUsdPrice();
            double usdBalance = position.getTokenBalance() * usdPrice;
            position.setUsdBalance(usdBalance);
        }
    }

    /**
     Jupiter API has a limit of 50 tokens per request (recently reduced from 100), so limit calls to 50 tokens at a time
     Jupiter Lite Price API docs: <a href="https://hub.jup.ag/docs/price-api/v3">...</a>
     */
    private String[] appendTokenIds(Map<String, Token> sessionTokenMap, Set<String> tokensWithNoMktData) {
        try {
            // Remove tokens with no mkt data from list of tokens to query
            Set<String> validTokensForMktDataQuery = sessionTokenMap.keySet();
            validTokensForMktDataQuery.removeAll(tokensWithNoMktData);

            int mktDataBatches = (int) Math.ceil((double) validTokensForMktDataQuery.size() / (s_jupTokenLimitRequest - 1));
            String[] tokenIdArray = new String[mktDataBatches];

            StringBuilder tokenIds = new StringBuilder();
            int batchCounter = 0;
            int tokenAppendCounter = 0;
            for (String tokenMintAddress : validTokensForMktDataQuery) {
                if (tokenAppendCounter != 0 && tokenAppendCounter % (s_jupTokenLimitRequest - 1) == 0 ) {
                    tokenIdArray[batchCounter] = tokenIds.toString();
                    batchCounter++;
                    tokenIds = new StringBuilder();
                    tokenAppendCounter = 0; // reset tokenAppendCounter
                }

                if (!tokensWithNoMktData.contains(tokenMintAddress)) {
                    tokenIds.append(tokenMintAddress).append(",");
                    tokenAppendCounter++;
                }
            }

            if (!tokenIds.isEmpty()) {
                // Add remaining token ids to last token Id array idx
                tokenIdArray[batchCounter] = tokenIds.toString();
            }

            return tokenIdArray;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Encountered exception while appending token IDs: " + e.getMessage());
        }

        return null;
    }

}
