package org.core.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.core.processors.MarketDataProcessor;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RestApiUtil {

    private static final Logger logger = Logger.getLogger(MarketDataProcessor.class.getName());
    private static final String s_jupiterPriceApi = "https://lite-api.jup.ag/price/v3?ids=";
    private static final String s_heliusTokenApi = "https://mainnet.helius-rpc.com/?api-key=";
    private static final String s_getRequest = "GET";
    private static final String s_postRequest = "POST";
    private static final String s_heliusTokenApiKey;

    static {
        String tempHeliusTokenApiKey;
        try {
            tempHeliusTokenApiKey = getHeliusApiKey();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to load Helius API key from properties file", e);
            tempHeliusTokenApiKey = StringUtils.EMPTY;
        }
        s_heliusTokenApiKey = tempHeliusTokenApiKey;
    }

    public static String getHeliusApiKey() throws IOException {
        Properties props = new Properties();
        try (InputStream input = RestApiUtil.class.getClassLoader().getResourceAsStream("secrets.properties")) {
            props.load(input);
        }

        return props.getProperty("helius.apiKey");
    }

    public static JSONObject getTokenMetadataFromHelius(String mintAddress) throws JsonProcessingException {
        ObjectNode params = JsonNodeFactory.instance.objectNode();
        params.put("id", mintAddress);

        ObjectNode displayOptions = JsonNodeFactory.instance.objectNode();
        displayOptions.put("showFungible", true);
        params.set("displayOptions", displayOptions);

        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("jsonrpc", "2.0");
        root.put("id", "test");
        root.put("method", "getAsset");
        root.set("params", params);

        String requestBody = new ObjectMapper().writeValueAsString(root);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(s_heliusTokenApi + s_heliusTokenApiKey))
                .header("Content-Type", "application/json")
                .header("Accept", "*/*")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        return sendHttpRequest(HttpClient.newHttpClient(), request);
    }

    public static JSONObject getMarketDataForTokens(HttpClient httpClient, String tokenIds) {

        HttpRequest request = getHttpRequest(s_getRequest, s_jupiterPriceApi + tokenIds);
        JSONObject response = sendHttpRequest(httpClient, request);

        if (response != null) { // TODO: Encountered exception here where "data" not found in response
            if (ValidationUtil.checkRateLimitException(response)){
                logger.log(Level.SEVERE, "Rate limit exceeded for Jupiter Price API");
            } else if (response.has("error")) {
                logger.log(Level.SEVERE, "Error in response: " + response.getString("error"));
            }
        }

        return response;
    }

    private static HttpRequest getHttpRequest(String requestType, String url) {

        switch (requestType) {
            case s_getRequest:
                return HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();
            case s_postRequest:
                // TODO: Implement POST request
        }

        return null;
    }

    private static JSONObject sendHttpRequest(HttpClient httpClient, HttpRequest request) {
        try {
            //TODO: Can send as an asyncrhonous call (sendAsync
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            if (ValidationUtil.checkRateLimitException(response)) {
                logger.log(Level.SEVERE, "Rate limit exceeded for Jupiter API");
                return null;
            }

            // Parse the JSON response
            return new JSONObject(responseBody);
        } catch (IOException | InterruptedException | JSONException e) {
            logger.log(Level.SEVERE, e.toString());
        }

        return null;
    }


}
