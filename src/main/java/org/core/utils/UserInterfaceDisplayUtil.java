package org.core.utils;

import javafx.scene.control.TextArea;
import org.core.accounts.Position;
import org.core.accounts.Token;
import org.core.accounts.Wallet;

import javax.swing.*;
import java.util.*;

public class UserInterfaceDisplayUtil {

    private static final String s_newLine = "\n";

    /*
     *  - TODO: Need to handle rate limit API calls for jupiter as loads of token info ends up missing
        - TODO: Need to filter out spam coins as these are dangerous (Add additional filter to filter by liquidity for a given market?
        - TODO: Clean display once done
     */
    public static void displayCommonTokensAcrossWallets(TextArea outputArea, Map<String, Wallet> wallets) {
        HashMap<String, List<Position>> tokenToPositionsMapping = new HashMap<>(); // Each position contains a reference to wallet

        for (Wallet wallet : wallets.values()) {
            for (Position position : wallet.getPositions().values()) {
                String tokenMintAddress = position.getToken().getMintAddress();
                if (tokenToPositionsMapping.containsKey(tokenMintAddress)) {
                    List<Position> positionsWithGivenToken = tokenToPositionsMapping.get(tokenMintAddress);
                    positionsWithGivenToken.add(position);
                } else {
                    List<Position> positionsWithGivenToken = new ArrayList<>();
                    positionsWithGivenToken.add(position);
                    tokenToPositionsMapping.put(tokenMintAddress, positionsWithGivenToken);
                }
            }
        }


        // Write filter function which removes positions where sum USD value is less than 10k
        filterPositionsByBalance(tokenToPositionsMapping);


        for (Map.Entry<String, List<Position>> entry : tokenToPositionsMapping.entrySet()) {
            String tokenMintAddress = entry.getKey();
            List<Position> positionsWithGivenToken = entry.getValue();
            if (positionsWithGivenToken.size() > 1) {
                outputArea.appendText("--------------------" + s_newLine);
                outputArea.appendText("Token Mint Address: " + tokenMintAddress + " (" + positionsWithGivenToken.get(0).getToken().getName() + ")" + s_newLine);
                outputArea.appendText("Wallets (" + positionsWithGivenToken.size() + "): " + s_newLine);
                for (Position position : positionsWithGivenToken) {
                    outputArea.appendText("     " + position.getWalletAddress() + " ->      USD Balance: " + position.getUsdBalance() + "/ Token Balance: " + position.getTokenBalance() +  s_newLine);
                }
                outputArea.appendText("--------------------" + s_newLine);
            }
        }
    }

    public static void displayCommonTokensAcrossWalletsCmd(JTextArea outputArea, Map<String, Wallet> wallets, Map<String, Token> tokenMap) {
        HashMap<String, List<Position>> tokenToPositionsMapping = new HashMap<>(); // Each position contains a reference to wallet

        for (Wallet wallet : wallets.values()) {
            for (Position position : wallet.getPositions().values()) {
                String tokenMintAddress = position.getToken().getMintAddress();
                if (tokenToPositionsMapping.containsKey(tokenMintAddress)) {
                    List<Position> positionsWithGivenToken = tokenToPositionsMapping.get(tokenMintAddress);
                    positionsWithGivenToken.add(position);
                } else {
                    List<Position> positionsWithGivenToken = new ArrayList<>();
                    positionsWithGivenToken.add(position);
                    tokenToPositionsMapping.put(tokenMintAddress, positionsWithGivenToken);
                }
            }
        }

        // Write filter function which removes positions where sum USD value is less than 10k
        filterPositionsByBalance(tokenToPositionsMapping);

        for (Map.Entry<String, List<Position>> entry : tokenToPositionsMapping.entrySet()) {
            String tokenMintAddress = entry.getKey();
            List<Position> positionsWithGivenToken = entry.getValue();
            double totalBalance = 0;
            if (positionsWithGivenToken.size() > 1) {
                outputArea.append("--------------------" + s_newLine);
                outputArea.append("Token Mint Address: " + tokenMintAddress + " (" + tokenMap.get(tokenMintAddress).getName() + ")" + s_newLine);
                outputArea.append("Wallets (" + positionsWithGivenToken.size() + "): " + s_newLine);
                for (Position position : positionsWithGivenToken) {
                    outputArea.append("     " + position.getWalletAddress() + " ->      USD Balance: " + position.getUsdBalance() + "/ Token Balance: " + position.getTokenBalance() +  s_newLine);
                    totalBalance = totalBalance + position.getUsdBalance();
                }
                outputArea.append("Total USD value: " + totalBalance + s_newLine);
                outputArea.append("--------------------" + s_newLine);
            }
        }
    }

    public static void displayWalletHoldings(TextArea outputArea, Map<String, Wallet> wallets) {
        if (wallets.isEmpty()) {
            outputArea.appendText("No wallets to display\n");
            return;
        }

        for (Wallet wallet : wallets.values()) {
            //outputArea.appendText("*****************************************************************************************" + s_newLine);
            outputArea.appendText("💼 Wallet Address: " + wallet.getAddress() + s_newLine);
            outputArea.appendText("💸 Wallet SOL Balance: " + wallet.getSolBalance() + s_newLine);
            //outputArea.append("SOl Balance in USD: " + wallet.getUsdBalance() + s_newLine);
            outputArea.appendText("🪙 Tokens: " + s_newLine);
            for (Position position : wallet.getPositions().values()) {
                //outputArea.appendText("--------------------" + s_newLine);
                outputArea.appendText(String.format("- Token Name: %s\n", position.getToken().getName()));
                outputArea.appendText(String.format("- Token Ticker: %s\n", position.getToken().getTicker()));
                outputArea.appendText(String.format("- Token Mint Address: %s\n", position.getToken().getMintAddress()));
                outputArea.appendText(String.format("- Token Balance: %s\n", position.getTokenBalance()));
                if (position.getToken().getMarketData() == null){
                    outputArea.appendText("- Token USD Price: N/A\n");
                    outputArea.appendText("- Token USD Value: N/A\n");
                } else {
                    outputArea.appendText(String.format("- Token USD Price: %f\n", position.getToken().getMarketData().getUsdPrice()));
                    outputArea.appendText(String.format("- Token USD Value: %f\n", position.getUsdBalance()));
                }
            }
            //outputArea.appendText("*****************************************************************************************" + s_newLine);
        }

    }

    public static void displayWalletHoldingsToDelete(JTextArea outputArea, Map<String, Wallet> wallets) {
        if (wallets.isEmpty()) {
            outputArea.append("No wallets to display\n");
            return;
        }

        for (Wallet wallet : wallets.values()) {
            outputArea.append("*****************************************************************************************" + s_newLine);
            outputArea.append("\uD83D\uDCBC Wallet Address: " + wallet.getAddress() + s_newLine);
            outputArea.append("Wallet SOL Balance: " + wallet.getSolBalance() + s_newLine);
            //outputArea.append("SOl Balance in USD: " + wallet.getUsdBalance() + s_newLine);
            outputArea.append("Tokens: " + s_newLine);
            for (Position position : wallet.getPositions().values()) {
                outputArea.append("--------------------" + s_newLine);
                outputArea.append(String.format("     Token Name: %s\n", position.getToken().getName()));
                outputArea.append(String.format("     Token Ticker: %s\n", position.getToken().getTicker()));
                outputArea.append(String.format("     Token Mint Address: %s\n", position.getToken().getMintAddress()));
                outputArea.append(String.format("     Token Balance: %s\n", position.getTokenBalance()));
                if (position.getToken().getMarketData() == null){
                    outputArea.append("     Token USD Price: N/A\n");
                    outputArea.append("     Token USD Value: N/A\n");
                } else {
                    outputArea.append(String.format("     Token USD Price: %f\n", position.getToken().getMarketData().getUsdPrice()));
                    outputArea.append(String.format("     Token USD Value: %f\n", position.getUsdBalance()));
                }
            }
            outputArea.append("*****************************************************************************************" + s_newLine);
        }

    }

    private static void filterPositionsByBalance(Map<String, List<Position>> tokenToPositionsMapping) {
        Iterator<Map.Entry<String, List<Position>>> iterator = tokenToPositionsMapping.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, List<Position>> entry = iterator.next();
            List<Position> positions = entry.getValue();
            double totalBalance = 0;
            for (Position position : positions) {
                totalBalance += position.getUsdBalance();
            }
            if (totalBalance < 10000) {
                iterator.remove();
            }
        }

    }
}
