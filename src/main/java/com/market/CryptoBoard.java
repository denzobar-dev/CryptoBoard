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

    private final Map<String, Double> lastPrices = new ConcurrentHashMap<>();
    private final Map<String, String> displayLines = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Pattern pricePattern = Pattern.compile("\"price\":\"([^\"]+)\"");

    private final String[] tickers = {"BTCUSDT", "ETHUSDT", "SOLUSDT", "TONUSDT"};
    private int animTick = 0;

    @Override
    public void onEnable() {
        // Опрос биржи каждые 5 секунд
        Bukkit.getAsyncScheduler().runAtFixedRate(this, task -> {
            for (String ticker : tickers) {
                fetchPrice(ticker);
            }
        }, 1, 5, java.util.concurrent.TimeUnit.SECONDS);

        // Тик анимации мигания LIVE раз в секунду
        Bukkit.getAsyncScheduler().runAtFixedRate(this, task -> {
            animTick++;
        }, 1, 1, java.util.concurrent.TimeUnit.SECONDS);

        new CryptoExpansion().register();
        getLogger().info("CryptoBoard запущен!");
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
                            double currentPrice = Double.parseDouble(matcher.group(1));
                            double oldPrice = lastPrices.getOrDefault(symbol, currentPrice);

                            String color;
                            String arrow;

                            if (currentPrice > oldPrice) {
                                color = "&a"; // Зеленый
                                arrow = "▲";
                            } else if (currentPrice < oldPrice) {
                                color = "&c"; // Красный
                                arrow = "▼";
                            } else {
                                color = "&f"; // Белый
                                arrow = "■";
                            }

                            lastPrices.put(symbol, currentPrice);

                            String priceStr = (currentPrice < 1)
                                    ? String.format("%.4f", currentPrice)
                                    : String.format("%,.2f", currentPrice);

                            // Формат для каждого тикера: $64,250.00 ▲
                            String formatted = String.format("%s$%s %s", color, priceStr, arrow);
                            displayLines.put(symbol.toLowerCase(), formatted);
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
        public @NotNull String getVersion() { return "3.0"; }
        @Override
        public boolean persist() { return true; }

        @Override
        public String onPlaceholderRequest(Player player, @NotNull String params) {
            String p = params.toLowerCase();

            // Вся лента котировок в одну цельную строку: %crypto_line%
            if (p.equals("line")) {
                return String.format("&6&lBTC &r%s &8| &6&lETH &r%s &8| &6&lSOL &r%s &8| &6&lTON &r%s",
                        displayLines.getOrDefault("btcusdt", "&7..."),
                        displayLines.getOrDefault("ethusdt", "&7..."),
                        displayLines.getOrDefault("solusdt", "&7..."),
                        displayLines.getOrDefault("tonusdt", "&7...")
                );
            }

            // Мигающая точка LIVE: %crypto_live%
            if (p.equals("live")) {
                return (animTick % 2 == 0) ? "&a● &2LIVE" : "&2○ &aLIVE";
            }

            // Отдельные тикеры, если понадобятся: %crypto_btcusdt%
            return displayLines.getOrDefault(p, "&7...");
        }
    }
}
