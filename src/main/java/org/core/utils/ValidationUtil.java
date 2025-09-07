package org.core.utils;

import org.json.JSONObject;

import java.net.http.HttpResponse;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ValidationUtil {

    private static final Logger logger = Logger.getLogger(ValidationUtil.class.getName());

    // TODO: Need to rewrite to cater for wallet 'name' label
    public static boolean validateAddressFormat(String address) {
        String[] arr = address.split(":"); // Split by space due to 'address name' format
        if (address.length() != 43 && address.length() != 44  && arr.length != 2) {
            logger.log(Level.WARNING, "Address length is not 44/45 characters OR address/name hasn't been entered");
            return false;
        }

        return true;
    }

    public static boolean checkRateLimitException(JSONObject tokenJupDetails) {
        return tokenJupDetails.has("status") && tokenJupDetails.getInt("status") == 429;
    }

    public static boolean checkRateLimitException(HttpResponse<String> tokenJupDetails) {
        return tokenJupDetails.statusCode() == 429 && tokenJupDetails.body().contains("Rate limit exceeded");
    }
}
