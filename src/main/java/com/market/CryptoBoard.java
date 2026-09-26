package com.market;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CryptoBoard extends JavaPlugin {

    // Хранилище актуальных цен в памяти
    private final Map<String, String> prices = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Pattern pricePattern = Pattern.compile("\"price\":\"([^\"]+)\"");

    // Список тикеров, которые мониторим
    private final String[] tickers = {"BTCUSDT", "ETHUSDT", "SOLUSDT", "TONUSDT"};

    @Override
    public void onEnable() {
        // Регулярное обновление раз в 15 секунд в фоновом потоке
        Bukkit.getAsyncScheduler().runAtFixedRate(this, task -> {
            for (String ticker : tickers) {
                fetchPrice(ticker);
            }
        }, 1, 15, java.util.concurrent.TimeUnit.SECONDS);

        // Регистрация в PlaceholderAPI
        new CryptoExpansion().register();
        getLogger().info("CryptoBoard запущен и поставляет котировки!");
    }

    private void fetchPrice(String symbol) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.binance.com/api/v3/ticker/price?symbol=" + symbol))
                    .GET()
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenAccept(body -> {
                        Matcher matcher = pricePattern.matcher(body);
                        if (matcher.find()) {
                            double val = Double.parseDouble(matcher.group(1));
                            String formatted = (val < 1) 
                                    ? String.format("%.4f", val) 
                                    : String.format("%,.2f", val);
                            prices.put(symbol.toLowerCase(), formatted);
                        }
                    });
        } catch (Exception ignored) {}
    }

    private class CryptoExpansion extends PlaceholderExpansion {
        @Override
        public @NotNull String getIdentifier() { return "crypto"; }
        @Override
        public @NotNull String getAuthor() { return "Dev"; }
        @Override
        public @NotNull String getVersion() { return "1.0"; }
        @Override
        public boolean persist() { return true; }

        @Override
        public String onPlaceholderRequest(Player player, @NotNull String params) {
            // Запрос вида: %crypto_btcusdt%
            return prices.getOrDefault(params.toLowerCase(), "Загрузка...");
        }
    }
}
