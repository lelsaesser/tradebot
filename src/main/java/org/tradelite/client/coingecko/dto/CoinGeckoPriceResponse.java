package org.tradelite.client.coingecko.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.tradelite.common.CoinId;

import java.util.HashMap;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinGeckoPriceResponse {
    private Map<String, CoinData> coinData = new HashMap<>();

    public Map<String, CoinData> getCoinData() {
        return coinData;
    }

    @JsonAnySetter
    public void setCoinData(String coinName, CoinData data) {
        this.coinData.put(coinName, data);
    }

    @Data
    public static class CoinData {
        @JsonProperty("usd")
        private double usd;
        @JsonProperty("usd_24h_change")
        private double usd_24h_change;

        private CoinId coinId;
    }

}
