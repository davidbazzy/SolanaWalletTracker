package org.core.helius;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HeliusAssetResponse {
    public String jsonrpc; //TODO: could delete unused fields or no?
    public String id;
    public AssetResult result;

    // inner class for result
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AssetResult {
        public String id;
        public String interfaceType;
        public Content content;

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Content {
            public Metadata metadata;
            public String json_uri;

            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class Metadata {
                public String name;
                public String symbol;
                public String description;
            }
        }

        @JsonSetter("token_info")
        public TokenInfo tokenInfo;

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class TokenInfo {
            public int decimals;
        }
    }
}

