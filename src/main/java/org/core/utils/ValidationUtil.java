package org.core.utils;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONObject;

import java.net.http.HttpResponse;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ValidationUtil {

    private static final Logger logger = Logger.getLogger(ValidationUtil.class.getName());

    // TODO: Need to rewrite to cater for wallet 'name' label
    public static Pair<String,String> extractNameAndAddress(String address) {
        String[] arr = address.split(":"); // Split by space due to 'address name' format
        if (arr.length != 2 || (arr[1].length() != 43 && arr[1].length() != 44)) {
            logger.log(Level.WARNING, "Address length is not 44/45 characters OR address/name hasn't been entered");
            return null;
        }

        return new ImmutablePair<>(arr[0], arr[1]);
    }

    public static boolean checkRateLimitException(JSONObject tokenJupDetails) {
        return tokenJupDetails.has("status") && tokenJupDetails.getInt("status") == 429;
    }

    public static boolean checkRateLimitException(HttpResponse<String> tokenJupDetails) {
        return tokenJupDetails.statusCode() == 429 && tokenJupDetails.body().contains("Rate limit exceeded");
    }
}
