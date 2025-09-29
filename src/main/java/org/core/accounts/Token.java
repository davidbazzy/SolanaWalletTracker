package org.core.accounts;

import org.core.prices.MarketData;

public class Token {

    private final String mintAddress; //address of the mint (CA)

    private final String name; //human-readable name of the token

    private final String ticker; //human-readable name of the token

    private final int decimals;

    private MarketData marketData;

    public Token(String mintAddress, String name, String ticker, int decimals) {
        this.mintAddress = mintAddress;
        this.name = name;
        this.ticker = ticker;
        this.decimals = decimals;
    }

    public MarketData getMarketData() {
        return marketData;
    }

    public void setMarketData(MarketData marketData) {
        this.marketData = marketData;
    }

    public String getName() {
        return name;
    }

    public String getMintAddress() {
        return mintAddress;
    }

    public String getTicker() {
        return ticker;
    }

    public int getDecimals() {
        return decimals;
    }

}