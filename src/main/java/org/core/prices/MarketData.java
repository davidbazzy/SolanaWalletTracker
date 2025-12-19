package org.core.prices;

public class MarketData {

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
