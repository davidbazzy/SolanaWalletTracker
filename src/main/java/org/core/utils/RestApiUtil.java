package org.core.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.core.processors.MarketDataProcessor;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RestApiUtil {

    private static final Logger logger = Logger.getLogger(MarketDataProcessor.class.getName());

    private static final String s_jupiterPriceApi = "https://api.jup.ag/price/v2?ids=";
    private static final String s_heliusTokenApi = "https://mainnet.helius-rpc.com/?api-key=";
    private static final String s_getRequest = "GET";
    private static final String s_postRequest = "POST";

    public static JSONObject getTokenMetadataFromHelius(String mintAddress) throws JsonProcessingException {
        final String heliusApiKey = ""; // TODO: Move and set api key in config that won't be uploaded to Github. Inject config value into class

        ObjectNode params = JsonNodeFactory.instance.objectNode();
        params.put("id", mintAddress);
        params.with("displayOptions").put("showFungible", true); //TODO: update as 'with' is deprecated in Jackson 2.17.0

        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("jsonrpc", "2.0");
        root.put("id", "test");
        root.put("method", "getAsset");
        root.set("params", params);

        String requestBody = new ObjectMapper().writeValueAsString(root);


        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(s_heliusTokenApi + heliusApiKey))
                .header("Content-Type", "application/json")
                .header("Accept", "*/*")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        return sendHttpRequest(HttpClient.newHttpClient(), request);
    }

    public static JSONObject getMarketDataForTokens(HttpClient httpClient, String tokenIds) {

        HttpRequest request = getHttpRequest(s_getRequest, s_jupiterPriceApi + tokenIds);
        JSONObject response = sendHttpRequest(httpClient, request);

        JSONObject data = null;
        if (response != null) { // TODO: Encountered exception here where "data" not found in response
            if (ValidationUtil.checkRateLimitException(response)){
                logger.log(Level.SEVERE, "Rate limit exceeded for Jupiter Price API");
            } else if (response.has("data")) {
                data = response.getJSONObject("data");
            } else if (response.has("error")) {
                logger.log(Level.SEVERE, "Error in response: " + response.getString("error"));
            } else {
                logger.log(Level.SEVERE, "Unexpected response format: " + response);
            }
        }

        return data;
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
