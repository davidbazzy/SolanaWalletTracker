package org.core.accounts;


import java.util.HashMap;
import java.util.Map;
import software.sava.core.accounts.PublicKey;

public class Wallet {

    private final String address;
    private final String name;
    private final double solBalance;
    private final PublicKey publicKey;
    private final Map<String,Position> positions;
    // TODO: Could store wallet total balance in terms of sum of all position balances? Can help evaluate what % of a persons total balance is in a particular token

    public Wallet(String address, String name, long lamports, PublicKey publicKey) {
        this.address = address;
        this.solBalance = lamports / 1000000000.0;
        this.publicKey = publicKey;
        this.positions = new HashMap<>();
        this.name = name;
    }

    public Map<String, Position> getPositions() {
        return positions;
    }

    public void addPosition(Position position) {
        positions.put(position.getAccountAddress(), position);
    }

    public String getAddress() {
        return address;
    }

    public double getSolBalance() {
        return solBalance;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public String getName() {
        return name;
    }
}