package org.core.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.tuple.Pair;
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
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

// Wallet service to handle wallet-related operations
public class WalletService {

    private final SolanaRpcClient m_solanaRpc;
    private final Map<String, Wallet> m_wallets;
    private final ConcurrentHashMap<String, Token> m_tokenMap;
    private final ConcurrentHashMap<String, Token> m_sessionTokenMap;
    private final Connection m_dbConnection;

    private static final Logger logger = Logger.getLogger(WalletService.class.getName());
    private static final PublicKey s_Token_Program_Public_Key = PublicKey.fromBase58Encoded("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA");
    private static final String s_unknownToken = "Unknown Token";
    private static final String s_unknownSymbol = "Unknown Symbol";

    // Virtual thread executor for fetching tokens
    private final ExecutorService m_virtualTokenThreadExecutor;

    // Rate limiter to manage Helius API rate limits + non-blocking on main thread
    private final Semaphore m_heliusRateLimiter;

    public WalletService(SolanaRpcClient solanaRpc, Map<String, Wallet> wallets, ConcurrentHashMap<String, Token> tokenMap,
                         ConcurrentHashMap<String, Token> sessionTokenMap, Connection dbConnection) {
        m_solanaRpc = solanaRpc;
        m_wallets = wallets;
        m_tokenMap = tokenMap;
        m_dbConnection = dbConnection;
        m_sessionTokenMap = sessionTokenMap;
        m_virtualTokenThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        m_heliusRateLimiter = new Semaphore(5);
    }

    public void processWalletForCmd(JTextArea outputArea, Pair<String, String> walletAddressAndLabel) {
        Wallet wallet;

        String walletAddress = walletAddressAndLabel.getRight();
        String walletName = walletAddressAndLabel.getLeft();

        logger.log(Level.INFO, "Fetching wallet details for: " + walletName);

        if (m_wallets.containsKey(walletAddress)) { // TODO: Will need to consider cases where a token has been sold from a wallet
            wallet = m_wallets.get(walletAddress);
            DatabaseConnUtil.persistWalletToDb(m_dbConnection, wallet); // Updates existing sol balance in db for wallet
            processWalletTokens(wallet);
        } else {
            PublicKey publicKey = PublicKey.fromBase58Encoded(walletAddress);
            AccountInfo<byte[]> accountInfo = getAccount(m_solanaRpc, publicKey);

            if (accountInfo != null) {
                wallet = new Wallet(walletAddress, walletName, accountInfo.lamports(), publicKey);
                processWalletTokens(wallet);
                DatabaseConnUtil.persistWalletToDb(m_dbConnection, wallet);
                m_wallets.put(walletAddress, wallet);
            } else {
                wallet = null;
                logger.log(Level.WARNING, "AccountInfo is null for wallet address: " + walletAddress);
            }
        }

        outputArea.append(String.format("✅ Wallet contents for %s retrieved successfully \n", walletAddress));

        if (wallet != null) {
            logger.log(Level.INFO, String.format("✅ Wallet contents for %s retrieved successfully \n", walletAddress));
            outputArea.append(String.format("🔃 Number of active positions: %s \n", wallet.getPositions().size()));
        }
    }

    public void processWalletTokens(Wallet wallet) {
        // Retrieve and parse token accounts in AccountInfoList to get tokens and store these in the wallet object
        List<AccountInfo<TokenAccount>> accountInfoList = getAccountList(m_solanaRpc, wallet.getPublicKey());

        if (accountInfoList != null) {
            parseTokenAccounts(accountInfoList, wallet);
        }
    }

    /**
     *  Using CompletableFutures to source token details asynchronously as this is much faster than doing this in just one thread (synchronously)
     *  Execution time for a wallet with 43 tokens was 0.17s (as opposed to 3.6s synchronously)
     */
    private void parseTokenAccounts(List<AccountInfo<TokenAccount>> accountInfoList, Wallet wallet) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        //Log time taken to parse through all token accounts for a given wallet
        long startTime = System.nanoTime();
        int dbFetchedTokens = 0;
        int heliusFetchedTokens = 0;

        for (AccountInfo<TokenAccount> accountInfo : accountInfoList) {
            TokenAccount tokenAccount = accountInfo.data();
            final String tokenMintAddress = tokenAccount.mint().toBase58();

            // Filter accounts whose balance is 0
            if (tokenAccount.amount() == 0) continue;

            boolean tokenExistsinMap = m_tokenMap.containsKey(tokenMintAddress);

            if (!tokenExistsinMap) {
                heliusFetchedTokens++;
                logger.log(Level.INFO, String.format("Fetching Metadata for Token #%d: %s from Helius", heliusFetchedTokens + dbFetchedTokens, tokenMintAddress));
            } else {
                dbFetchedTokens++;
                logger.log(Level.INFO, String.format("Fetching Metadata for Token #%d: %s loaded from DB", dbFetchedTokens + heliusFetchedTokens, tokenMintAddress));
            }

            futures.add(fetchTokenDetails(tokenMintAddress, wallet, tokenAccount, tokenExistsinMap));
        }

        if (!futures.isEmpty()) {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            logger.log(Level.INFO, "Completed fetching token details using futures for wallet: " + wallet.getName());
        } else {
            logger.log(Level.SEVERE, String.format("Futures call to fetch token details is empty for wallet: %s. Something has gone wrong while processing tokens", wallet.getName()));
        }

        long endTime = System.nanoTime();
        double duration = (double) (endTime - startTime) / 1000000000; // Duration in seconds
        logger.log(Level.INFO,String.format( "ParseTokenAccounts() execution time for wallet %s & %d tokens: %f seconds", wallet.getAddress(), dbFetchedTokens + heliusFetchedTokens, duration));
    }

    /**
     * Fetch token details asynchronously using Completable Futures and virtual threads.
     * Use of virtual threads due to less memory overhead vs threads + token calls are I/O bound operations
     */
    private CompletableFuture<Void> fetchTokenDetails(String tokenMintAddress, Wallet wallet,
                                                      TokenAccount tokenAccount, boolean tokenExistsinMap) {

        // Asynchronously fetch token details - runAsync uses the configured executor (virtual threads when available)
        return CompletableFuture.runAsync(() -> {
            try {
                Token token;
                if (tokenExistsinMap) {
                    token = m_tokenMap.get(tokenMintAddress);
                } else {
                    // Fetch token detail from Helius API - acquire rate limit permit
                    m_heliusRateLimiter.acquire();
                    try {
                        // Query Helius API for token & persist to DB
                        token = createTokenUsingHelius(tokenMintAddress);
                        DatabaseConnUtil.persistTokenToDb(m_dbConnection, token);
                        m_tokenMap.put(tokenMintAddress, token);

                        // Add small delay to respect rate limits (adjustable)
                        Thread.sleep(200); // 200ms = ~5 requests/second
                    } finally {
                        // Always release the permit
                        m_heliusRateLimiter.release();
                    }
                }

                m_sessionTokenMap.put(tokenMintAddress, token);
                double balance = tokenAccount.amount() / Math.pow(10, token.getDecimals());
                Position position = new Position(wallet.getAddress(), tokenAccount.address().toBase58(), token, balance);

                // TODO: TO BE REVIEWED, do we want to store position in db?
                //DatabaseConnUtil.persistPositionToDb(dbConn, wallet.getAddress(), position.getAccountAddress(), tokenMintAddress, token.getTicker(), balance);
                wallet.addPosition(position);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.log(Level.SEVERE, "Thread interrupted while processing token: " + tokenMintAddress, e);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error processing token: " + tokenMintAddress, e);
            }

        }, m_virtualTokenThreadExecutor);
    }

    private Token createTokenUsingHelius(String tokenMintAddress) {
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


    private AccountInfo<byte[]> getAccount(SolanaRpcClient rpcClient, PublicKey publicKey) {
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

    private List<AccountInfo<TokenAccount>> getAccountList(SolanaRpcClient rpcClient, PublicKey publicKey) {
        List<AccountInfo<TokenAccount>> accountList = null;

        CompletableFuture<List<AccountInfo<TokenAccount>>> accountListFuture = rpcClient.getTokenAccountsForProgramByOwner(publicKey, s_Token_Program_Public_Key);
        try {
            accountList = accountListFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            logger.log(Level.SEVERE, "Exception while retrieving accountInfo: " + e);
        }

        return accountList;

    }

    /**
     * Shutdown the executor when the service is no longer needed
     * Call this when your application is shutting down
     */
    public void shutdown() {
        logger.log(Level.INFO, "Shutting down WalletService virtual thread executor...");
        m_virtualTokenThreadExecutor.shutdown();
        try {
            if (!m_virtualTokenThreadExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                m_virtualTokenThreadExecutor.shutdownNow();
                logger.log(Level.WARNING, "Executor did not terminate in time, forced shutdown");
            }
        } catch (InterruptedException e) {
            m_virtualTokenThreadExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // TODO: To be used once I switch from cmd to JavaFX
    /*public Wallet processWalletForJavaFx(String walletAddress) {
        Wallet wallet;

        if (m_wallets.containsKey(walletAddress)) { // TODO: Will need to consider cases where a token has been sold from a wallet
            wallet = m_wallets.get(walletAddress);
            processWalletTokens(m_solanaRpc, wallet, m_tokenMap, m_dbConnection);
        } else {
            PublicKey publicKey = PublicKey.fromBase58Encoded(walletAddress);
            AccountInfo<byte[]> accountInfo = getAccount(m_solanaRpc, publicKey);

            if (accountInfo != null) {
                wallet = new Wallet(walletAddress, "temp name", accountInfo.lamports(), publicKey);
                processWalletTokens(m_solanaRpc, wallet, m_tokenMap, m_dbConnection);
                m_wallets.put(walletAddress, wallet);
            } else {
                wallet = null;
                logger.log(Level.WARNING, "AccountInfo is null for wallet walletInput: " + walletAddress);
            }
        }

        return wallet;
    }*/

}
