package org.core.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.core.helius.HeliusAssetResponse;
import org.core.accounts.Position;
import org.core.accounts.Token;
import org.core.accounts.Wallet;
import org.json.JSONException;
import org.json.JSONObject;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.token.TokenAccount;
import software.sava.rpc.json.http.client.SolanaRpcClient;
import software.sava.rpc.json.http.response.AccountInfo;

import javax.swing.*;
import java.sql.Connection;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;


public class SolanaWalletUtil {

    private static final Logger logger = Logger.getLogger(SolanaWalletUtil.class.getName());

    private static final PublicKey s_Token_Program_Public_Key = PublicKey.fromBase58Encoded("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA");

    private static final String s_unknownToken = "Unknown Token";

    private static final String s_unknownSymbol = "Unknown Symbol";

    public SolanaWalletUtil() {
    }

    public static Wallet processWallet(SolanaRpcClient rpcClient, Map<String, Wallet> wallets,
                                           String walletInput, Map<String, Token> tokenMap, Map<String, Token> activeTokenMap,
                                           Connection dbConn) {
        Wallet wallet;

        if (wallets.containsKey(walletInput)) { // TODO: Will need to consider cases where a token has been sold from a wallet
            wallet = wallets.get(walletInput);
            processWalletTokens(rpcClient, wallet, tokenMap, activeTokenMap, dbConn);
        } else {
            PublicKey publicKey = PublicKey.fromBase58Encoded(walletInput);
            AccountInfo<byte[]> accountInfo = getAccount(rpcClient, publicKey);

            if (accountInfo != null) {
                wallet = new Wallet(walletInput, "temp name", accountInfo.lamports(), publicKey);
                processWalletTokens(rpcClient, wallet, tokenMap, activeTokenMap, dbConn);
                wallets.put(walletInput, wallet);
            } else {
                wallet = null;
                logger.log(Level.WARNING, "AccountInfo is null for wallet walletInput: " + walletInput);
            }
        }

        return wallet;
    }
    public static void processWalletForCmd(JTextArea outputArea, SolanaRpcClient rpcClient, Map<String, Wallet> wallets,
                                     String walletInput, Map<String, Token> tokenMap, Map<String, Token> activeTokenMap,
                                     Connection dbConn) {
        Wallet wallet;
        String[] walletInputSplit = walletInput.split(" ");

        if (walletInputSplit.length != 2) {
            outputArea.append("❌ Invalid wallet input format. Please use: <wallet_address> <wallet_name>\n");
            return;
        }

        String walletAddress = walletInputSplit[0];
        String walletName = walletInputSplit[1];

        if (wallets.containsKey(walletAddress)) { // TODO: Will need to consider cases where a token has been sold from a wallet
            wallet = wallets.get(walletAddress);
            DatabaseConnUtil.persistWalletToDb(dbConn, wallet); // Updates existing sol balance attached to wallet
            processWalletTokens(rpcClient, wallet, tokenMap, activeTokenMap, dbConn);
        } else {
            PublicKey publicKey = PublicKey.fromBase58Encoded(walletAddress);
            AccountInfo<byte[]> accountInfo = getAccount(rpcClient, publicKey);

            if (accountInfo != null) {
                wallet = new Wallet(walletAddress, walletName, accountInfo.lamports(), publicKey);
                processWalletTokens(rpcClient, wallet, tokenMap, activeTokenMap, dbConn);
                DatabaseConnUtil.persistWalletToDb(dbConn, wallet);
                wallets.put(walletAddress, wallet);
            } else {
                wallet = null;
                logger.log(Level.WARNING, "AccountInfo is null for wallet address: " + walletAddress);
            }
        }

        outputArea.append(String.format("✅ Wallet contents for %s retrieved successfully \n", walletAddress));

        if (wallet != null) {
            outputArea.append(String.format("🔃 Active positions: %s \n", wallet.getPositions().size()));
        }

    }

    public static void processWalletTokens(SolanaRpcClient rpcClient, Wallet wallet, Map<String, Token> tokenMap, Map<String, Token> activeTokenMap,
                                           Connection dbConn) {
        // Retrieve and parse token accounts in AccountInfoList to get tokens and store these in the wallet object
        List<AccountInfo<TokenAccount>> accountInfoList = getAccountList(rpcClient, wallet.getPublicKey());

        if (accountInfoList != null) {
            parseTokenAccounts(accountInfoList, wallet, tokenMap, dbConn);
        }
    }

    /**
     *  Using CompletableFutures to source token details asynchronously as this is much faster than doing this in just one thread (synchronously)
     *  Execution time for a wallet with 43 tokens was 0.17s (as opposed to 3.6s synchronously)
     */
    public static void parseTokenAccounts(List<AccountInfo<TokenAccount>> accountInfoList, Wallet wallet,
                                          Map<String, Token> tokenMap, Connection dbConn) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        //Log time taken to parse through all token accounts for a given wallet
        long startTime = System.nanoTime();

        int heliusQueryCount = 1;
        int tokenCount = 0;

        for (AccountInfo<TokenAccount> accountInfo : accountInfoList) {
            TokenAccount tokenAccount = accountInfo.data();
            final String tokenMintAddress = tokenAccount.mint().toBase58();

            // Temporarily filter accounts whose balance is 0
            if (tokenAccount.amount() == 0) {
                continue;
            }

            try {
                if (heliusQueryCount % 6 == 0) {
                    Thread.sleep(3000); //sleep for 2s to manage rate limits (10 rqs)
                    logger.log(Level.INFO, "Sleeping for 2s... token #" + tokenCount);
                    heliusQueryCount++;
                }
            } catch (InterruptedException e) {
                logger.log(Level.SEVERE, "Thread interrupted: " + e.getMessage());
            }

            boolean tokenExistsinMap = tokenMap.containsKey(tokenMintAddress);

            if (!tokenExistsinMap) {
                logger.log(Level.INFO, "Fetching Metadata for Token #" + tokenCount + ": " + tokenMintAddress);
                heliusQueryCount++;
            } else {
                logger.log(Level.INFO, "Fetching Metadata for Token #" + tokenCount + " loaded from DB: " + tokenMintAddress);
            }

            // Asynchronously fetch token details
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {

                Token token;
                // If token is already in activeTokens, skip
                if (tokenExistsinMap) {
                    token = tokenMap.get(tokenMintAddress);
                } else {
                    token = createTokenUsingHelius( tokenMintAddress);
                    DatabaseConnUtil.persistTokenToDb(dbConn, token);
                    tokenMap.put(token.getMintAddress(), token);
                }

                double balance = tokenAccount.amount() / Math.pow(10, token.getDecimals());
                Position position = new Position(wallet.getAddress(), tokenAccount.address().toBase58(), token, balance);
                DatabaseConnUtil.persistPositionToDb(dbConn, wallet.getAddress(), position.getAccountAddress(), tokenMintAddress, token.getTicker(), balance);
                wallet.addPosition(position);
            });

            futures.add(future);
            tokenCount++;
        }

        // Wait for all futures to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        long endTime = System.nanoTime();
        double duration = (double) (endTime - startTime) / 1000000000; // Duration in seconds
        logger.log(Level.INFO,String.format( "ParseTokenAccounts() execution time for wallet %s & %d tokens: %f seconds", wallet.getAddress(), tokenCount, duration));
    }

    private static Token createTokenUsingHelius(String tokenMintAddress) {
        JSONObject tokenDetailsHelius;

        try {
            tokenDetailsHelius = RestApiUtil.getTokenMetadataFromHelius(tokenMintAddress);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error fetching token metadata from Helius: " + e.getMessage());
            return new Token(tokenMintAddress, s_unknownToken, s_unknownSymbol, 0);
        }

        if (tokenDetailsHelius == null){
            logger.log(Level.WARNING, "Token details not found for mint address: " + tokenMintAddress);
            return new Token(tokenMintAddress, s_unknownToken, s_unknownSymbol, 0);
        }

        String tokenName;
        String tokenSymbol;
        int tokenDecimals;

        try {
            ObjectMapper mapper = new ObjectMapper();
            HeliusAssetResponse response = mapper.readValue(tokenDetailsHelius.toString(), HeliusAssetResponse.class);

            tokenName = Optional.ofNullable(response)
                    .map(r -> r.result)
                    .map(r -> r.content)
                    .map(c -> c.metadata)
                    .map(m -> m.name)
                    .orElse(s_unknownToken);

            tokenSymbol = Optional.ofNullable(response)
                    .map(r -> r.result)
                    .map(r -> r.content)
                    .map(c -> c.metadata)
                    .map(m -> m.symbol)
                    .orElse(s_unknownSymbol);

            tokenDecimals = Optional.ofNullable(response)
                    .map(r -> r.result)
                    .map(r -> r.tokenInfo)
                    .map(d -> d.decimals)
                    .orElse(0);

            if (s_unknownToken.equals(tokenName)){
                logger.log(Level.SEVERE, "Unknown Token for mint address: " + tokenMintAddress);
                logger.log(Level.SEVERE, tokenDetailsHelius.toString());
            }

            return new Token(tokenMintAddress, tokenName, tokenSymbol, tokenDecimals);
        } catch (JSONException | JsonProcessingException e) {
            logger.log(Level.WARNING, "Error parsing token details: " + e);
            tokenName = s_unknownToken;
            tokenSymbol = s_unknownSymbol;
            tokenDecimals = 0;
        }

        return new Token(tokenMintAddress, tokenName, tokenSymbol, tokenDecimals);
    }


    private static AccountInfo<byte[]> getAccount(SolanaRpcClient rpcClient, PublicKey publicKey) {
        AccountInfo<byte[]> accountInfo = null;

        try {
            CompletableFuture<AccountInfo<byte[]>> accountInfoFuture = rpcClient.getAccountInfo(publicKey);
            accountInfo = accountInfoFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            System.out.println("Exception while retrieving accountInfo: " + e);
        } catch (Exception e) {
            System.out.println("HTTP Connection Timeout Exception: " + e);
        }

        return accountInfo;
    }

    private static List<AccountInfo<TokenAccount>> getAccountList(SolanaRpcClient rpcClient, PublicKey publicKey) {
        List<AccountInfo<TokenAccount>> accountList = null;

        CompletableFuture<List<AccountInfo<TokenAccount>>> accountListFuture = rpcClient.getTokenAccountsForProgramByOwner(publicKey, s_Token_Program_Public_Key);
        try {
            accountList = accountListFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            logger.log(Level.SEVERE, "Exception while retrieving accountInfo: " + e);
        }

        return accountList;

    }

}
