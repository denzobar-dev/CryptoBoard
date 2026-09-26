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
        // Опрос биржи каждые 5 секунд для живой динамики
        Bukkit.getAsyncScheduler().runAtFixedRate(this, task -> {
            for (String ticker : tickers) {
                fetchPrice(ticker);
            }
        }, 1, 5, java.util.concurrent.TimeUnit.SECONDS);

        // Анимация индикатора LIVE (мигающая неоновая точка раз в секунду)
        Bukkit.getAsyncScheduler().runAtFixedRate(this, task -> {
            animTick++;
        }, 1, 1, java.util.concurrent.TimeUnit.SECONDS);

        new CryptoExpansion().register();
        getLogger().info("CryptoBoard биржевой терминал активирован!");
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

                            // Рассчитываем динамику курса
                            String color;
                            String arrow;
                            double diffPercent = 0.0;

                            if (oldPrice > 0) {
                                diffPercent = ((currentPrice - oldPrice) / oldPrice) * 100.0;
                            }

                            if (currentPrice > oldPrice) {
                                color = "&a"; // Сочный зелёный при росте
                                arrow = "▲";
                            } else if (currentPrice < oldPrice) {
                                color = "&c"; // Яркий красный при падении
                                arrow = "▼";
                            } else {
                                color = "&f"; // Белый, если не изменилась
                                arrow = "■";
                            }

                            // Сохраняем текущую для следующего тика
                            lastPrices.put(symbol, currentPrice);

                            // Форматируем цену
                            String priceStr = (currentPrice < 1) 
                                    ? String.format("%.4f", currentPrice) 
                                    : String.format("%,.2f", currentPrice);

                            // Готовая биржевая строка: [ЦВЕТ] $64,250.00 ▲ (+0.12%)
                            String formatted = String.format("%s$%s &l%s &7(%s%.2f%%&7)", 
                                    color, priceStr, arrow, color, diffPercent);

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
        public @NotNull String getVersion() { return "2.0"; }
        @Override
        public boolean persist() { return true; }

        @Override
        public String onPlaceholderRequest(Player player, @NotNull String params) {
            String p = params.toLowerCase();

            // Анимированный индикатор работы биржи %crypto_live%
            if (p.equals("live")) {
                return (animTick % 2 == 0) ? "&a● &2LIVE" : "&2○ &aLIVE";
            }

            // Вывод строки с динамикой цен: %crypto_btcusdt%
            return displayLines.getOrDefault(p, "&7Загрузка данных...");
        }
    }
}
