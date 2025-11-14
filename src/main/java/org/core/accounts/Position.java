package org.core.accounts;

public class Position {
    private final String walletAddress;
    private final String accountAddress;
    private final Token token;
    private double tokenBalance; // balance in native ccy
    private double usdBalance; // balance in USD

    public Position(String walletAddress, String accountAddress, Token token, double lamports) {
        this.walletAddress = walletAddress;
        this.accountAddress = accountAddress;
        this.token = token;
        this.tokenBalance = lamports; // token balances are scaled by a factor of 1,000. Therefore, we divide by 1,000,000 rather than 1B (as per official lamport calcs)
    }

    public String getWalletAddress() {
        return walletAddress;
    }

    public String getAccountAddress() {
        return accountAddress;
    }

    public Token getToken() {
        return token;
    }

    public double getTokenBalance() {
        return tokenBalance;
    }

    public double getUsdBalance() {
        return usdBalance;
    }

    public void setUsdBalance(double usdBalance) {
        this.usdBalance = usdBalance;
    }
}
