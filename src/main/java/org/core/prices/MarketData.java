package org.core.prices;

public class MarketData {

    /*   Market data notes:

    - Going to create a 'product' type table (I'll use a dataset, list to start with) where for each token that's available in a wallet, we start an active market data feed for it
        -> Every seen token will be stored in the data set
    - The market data feed will be a THREAD which runs in the background and updates price of token every X seconds
    - The feed will provide SOL and USD value for token balance
     */

    private final String mintAddress; //address of the mint (CA)

    private double usdPrice;


    public MarketData(String mintAddress, double usdPrice) {
        this.mintAddress = mintAddress;
        this.usdPrice = usdPrice;
    }

    public double getUsdPrice() {
        return usdPrice;
    }

    public void setUsdPrice(double usdPrice) {
        if(usdPrice != this.usdPrice){
            this.usdPrice = usdPrice;
        }
    }
}
