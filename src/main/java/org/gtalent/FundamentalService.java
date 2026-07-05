package org.gtalent;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FundamentalService {
    private static final String[] REVENUE_FEEDS = {
            "https://mopsfin.twse.com.tw/opendata/t187ap05_L.csv",
            "https://mopsfin.twse.com.tw/opendata/t187ap05_O.csv",
            "https://mopsfin.twse.com.tw/opendata/t187ap05_P.csv"
    };
    private static final String GOODINFO_REVENUE_URL = "https://goodinfo.tw/tw/ShowSaleMonChart.asp?STOCK_ID=%s";
    private static final Pattern GOODINFO_CLIENT_KEY_PATTERN = Pattern.compile(
            "arr\\[0\\]\\s*=\\s*'([^']+)'.*?arr\\[1\\]\\s*=\\s*'([^']+)'.*?arr\\[2\\]\\s*=\\s*'([^']+)'.*?window\\.location\\.replace\\('([^']+)'\\)",
            Pattern.DOTALL);

    private static final String FIELD_YEAR_MONTH = normalizeHeader("資料年月");
    private static final String FIELD_SYMBOL = normalizeHeader("公司代號");
    private static final String FIELD_REVENUE = normalizeHeader("營業收入-當月營收");
    private static final String FIELD_MOM = normalizeHeader("營業收入-上月比較增減(%)");
    private static final String FIELD_YOY = normalizeHeader("營業收入-去年同月增減(%)");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public int calculateRevenueScore(List<RevenueData> revenueHistory) {
        if (revenueHistory == null || revenueHistory.size() < 3) return 0;

        int score = 0;
        RevenueData current = revenueHistory.get(revenueHistory.size() - 1);
        RevenueData p1 = revenueHistory.get(revenueHistory.size() - 2);
        RevenueData p2 = revenueHistory.get(revenueHistory.size() - 3);

        if (current.getYoy() > 0) {
            score += 5;
            if (current.getYoy() > p1.getYoy() && p1.getYoy() > p2.getYoy()) {
                score += 5;
            }
        }
        if (current.getYoy() > 30.0) {
            score += 2;
        }

        if (current.getRevenue() > p1.getRevenue() && p1.getRevenue() > p2.getRevenue()) {
            score += 5;
        }
        if (current.getMom() > 10.0) {
            score += 3;
        }

        // 減分：動能轉弱、趨勢惡化
        if (current.getYoy() < 0) {
            score -= 5;
        }
        if (current.getYoy() < p1.getYoy() && p1.getYoy() < p2.getYoy()) {
            score -= 3;
        }
        if (current.getRevenue() < p1.getRevenue() && p1.getRevenue() < p2.getRevenue()) {
            score -= 5;
        }
        if (current.getMom() < -10.0) {
            score -= 3;
        } else if (current.getMom() < 0.0 && p1.getMom() < 0.0) {
            score -= 2;
        }

        return Math.max(0, Math.min(20, score));
    }

    public List<RevenueData> getRevenueHistory(String symbol, int months) {
        String cleanSymbol = normalizeSymbol(symbol);
        if (cleanSymbol.isBlank()) {
            return List.of();
        }

        int safeMonths = Math.max(1, Math.min(months, 120));
        boolean refreshed = refreshLatestRevenueData(cleanSymbol);
        List<RevenueData> history = DatabaseManager.getRevenueHistory(cleanSymbol, safeMonths);
        if (history.size() >= safeMonths) {
            return history;
        }

        backfillRevenueHistory(cleanSymbol, safeMonths);
        history = DatabaseManager.getRevenueHistory(cleanSymbol, safeMonths);
        if (!refreshed || !history.isEmpty()) {
            return history;
        }

        refreshLatestRevenueData();
        return DatabaseManager.getRevenueHistory(cleanSymbol, safeMonths);
    }

    // For latency-sensitive flows (e.g., radar endpoint), avoid expensive history backfill.
    public List<RevenueData> getRevenueHistoryForScoring(String symbol, int months) {
        String cleanSymbol = normalizeSymbol(symbol);
        if (cleanSymbol.isBlank()) {
            return List.of();
        }

        int safeMonths = Math.max(1, Math.min(months, 24));
        List<RevenueData> history = DatabaseManager.getRevenueHistory(cleanSymbol, safeMonths);
        if (history.size() >= Math.min(3, safeMonths)) {
            return history;
        }

        refreshLatestRevenueData(cleanSymbol);
        return DatabaseManager.getRevenueHistory(cleanSymbol, safeMonths);
    }

    public RevenueData getLatestRevenueData(String symbol) {
        List<RevenueData> history = getRevenueHistory(symbol, 1);
        return history.isEmpty() ? null : history.get(history.size() - 1);
    }

    public int backfillRevenueHistory(String symbol, int months) {
        String cleanSymbol = normalizeSymbol(symbol);
        if (cleanSymbol.isBlank()) {
            return 0;
        }

        int safeMonths = Math.max(1, Math.min(months, 120));
        List<RevenueData> current = DatabaseManager.getRevenueHistory(cleanSymbol, safeMonths);
        if (current.size() >= safeMonths) {
            return 0;
        }

        List<RevenueData> fetched = fetchRevenueHistoryFromGoodinfo(cleanSymbol, safeMonths);
        for (RevenueData item : fetched) {
            DatabaseManager.saveRevenueData(cleanSymbol, item);
        }
        return fetched.size();
    }

    public int refreshLatestRevenueData() {
        int saved = 0;
        for (String feedUrl : REVENUE_FEEDS) {
            saved += fetchAndPersistRevenueFeed(feedUrl, null);
        }
        return saved;
    }

    public boolean refreshLatestRevenueData(String symbol) {
        String cleanSymbol = normalizeSymbol(symbol);
        if (cleanSymbol.isBlank()) {
            return false;
        }

        for (String feedUrl : REVENUE_FEEDS) {
            int saved = fetchAndPersistRevenueFeed(feedUrl, cleanSymbol);
            if (saved > 0) {
                return true;
            }
        }
        return false;
    }

    private List<RevenueData> fetchRevenueHistoryFromGoodinfo(String symbol, int months) {
        try {
            String initialUrl = String.format(Locale.ROOT, GOODINFO_REVENUE_URL, symbol);
            String initialHtml = downloadText(initialUrl, Map.of(
                    "User-Agent", "Mozilla/5.0",
                    "Accept-Language", "zh-TW,zh;q=0.9"
            ));

            Matcher matcher = GOODINFO_CLIENT_KEY_PATTERN.matcher(initialHtml);
            if (!matcher.find()) {
                return List.of();
            }

            String arr0 = matcher.group(1);
            String arr1 = matcher.group(2);
            String arr2 = matcher.group(3);
            String redirectPath = matcher.group(4);
            String reinit = extractReinit(redirectPath);
            int timezoneOffsetMinutes = -(int) (ZonedDateTime.now().getOffset().getTotalSeconds() / 60);
            String clientKey = String.format(Locale.ROOT, "%s|%s|%s|%d|%s|0|0|0", arr0, arr1, arr2, timezoneOffsetMinutes, reinit);

            String redirectedUrl = redirectPath.startsWith("http")
                    ? redirectPath
                    : "https://goodinfo.tw/tw/" + redirectPath.replaceFirst("^/+", "");
            String pageHtml = downloadText(redirectedUrl, Map.of(
                    "User-Agent", "Mozilla/5.0",
                    "Accept-Language", "zh-TW,zh;q=0.9",
                    "Referer", "https://goodinfo.tw/",
                    "Cookie", "CLIENT_KEY=" + clientKey
            ));

            return parseGoodinfoRevenueHistory(pageHtml, months);
        } catch (Exception e) {
            System.err.println("Goodinfo 月營收回補失敗(" + symbol + "): " + e.getMessage());
            return List.of();
        }
    }

    private String extractReinit(String redirectPath) {
        Matcher matcher = Pattern.compile("REINIT=([0-9.]+)").matcher(redirectPath == null ? "" : redirectPath);
        return matcher.find() ? matcher.group(1) : String.format(Locale.ROOT, "%.6f", System.currentTimeMillis() / 86400000.0);
    }

    private List<RevenueData> parseGoodinfoRevenueHistory(String html, int months) {
        if (html == null || html.isBlank()) {
            return List.of();
        }

        Document doc = Jsoup.parse(html);
        Element table = doc.selectFirst("table#tblDetail");
        if (table == null) {
            return List.of();
        }

        List<RevenueData> result = new ArrayList<>();
        Elements rows = table.select("tbody tr");
        for (Element row : rows) {
            Elements cells = row.select("td");
            if (cells.size() < 15) {
                continue;
            }

            String monthText = cleanCellText(cells.get(0).text());
            if (!monthText.matches("\\d{4}/\\d{2}")) {
                continue;
            }

            String yearMonth = monthText.replace('/', '-');
            double mergedRevenue = parseDouble(cells.get(12).text()) * 100_000_000.0;
            double mergedMom = parseDouble(cells.get(13).text());
            double mergedYoy = parseDouble(cells.get(14).text());
            double singleRevenue = parseDouble(cells.get(7).text()) * 100_000_000.0;
            double singleMom = parseDouble(cells.get(8).text());
            double singleYoy = parseDouble(cells.get(9).text());

            double revenue = mergedRevenue > 0 ? mergedRevenue : singleRevenue;
            double mom = mergedRevenue > 0 ? mergedMom : singleMom;
            double yoy = mergedRevenue > 0 ? mergedYoy : singleYoy;
            if (revenue <= 0) {
                continue;
            }

            result.add(new RevenueData(yearMonth, revenue, mom, yoy));
        }

        result.sort(Comparator.comparing(RevenueData::getYearMonth));
        if (result.size() <= months) {
            return result;
        }
        return new ArrayList<>(result.subList(result.size() - months, result.size()));
    }

    int fetchAndPersistRevenueFeed(String feedUrl, String symbolFilter) {
        try {
            String csv = downloadCsv(feedUrl);
            return parseAndSaveRevenueCsv(csv, symbolFilter);
        } catch (Exception e) {
            System.err.println("抓取月營收資料失敗(" + feedUrl + "): " + e.getMessage());
            return 0;
        }
    }

    int parseAndSaveRevenueCsv(String csv, String symbolFilter) {
        if (csv == null || csv.isBlank()) {
            return 0;
        }

        String[] lines = csv.replace("\uFEFF", "").split("\\r?\\n");
        if (lines.length < 2) {
            return 0;
        }

        Map<String, Integer> headerIndex = buildHeaderIndex(lines[0]);
        if (!headerIndex.containsKey(FIELD_SYMBOL)
                || !headerIndex.containsKey(FIELD_YEAR_MONTH)
                || !headerIndex.containsKey(FIELD_REVENUE)
                || !headerIndex.containsKey(FIELD_MOM)
                || !headerIndex.containsKey(FIELD_YOY)) {
            return 0;
        }

        String normalizedFilter = normalizeSymbol(symbolFilter);
        int saved = 0;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line == null || line.isBlank()) {
                continue;
            }

            List<String> cells = splitCsvLine(line);
            String symbol = getCell(cells, headerIndex.get(FIELD_SYMBOL));
            if (symbol.isBlank()) {
                continue;
            }
            if (!normalizedFilter.isBlank() && !normalizedFilter.equals(symbol)) {
                continue;
            }

            RevenueData revenueData = parseRevenueData(cells, headerIndex);
            if (revenueData == null) {
                continue;
            }

            DatabaseManager.saveRevenueData(symbol, revenueData);
            saved++;
        }
        return saved;
    }

    RevenueData parseRevenueData(List<String> cells, Map<String, Integer> headerIndex) {
        String yearMonth = toGregorianYearMonth(getCell(cells, headerIndex.get(FIELD_YEAR_MONTH)));
        if (yearMonth == null || yearMonth.isBlank()) {
            return null;
        }

        double revenue = parseDouble(getCell(cells, headerIndex.get(FIELD_REVENUE))) * 1000.0;
        double mom = parseDouble(getCell(cells, headerIndex.get(FIELD_MOM)));
        double yoy = parseDouble(getCell(cells, headerIndex.get(FIELD_YOY)));

        return new RevenueData(yearMonth, revenue, mom, yoy);
    }

    private String downloadCsv(String feedUrl) throws IOException, InterruptedException {
        return downloadText(feedUrl, Map.of(
                "User-Agent", "Mozilla/5.0"
        ));
    }

    private String downloadText(String url, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20));
        headers.forEach(builder::header);
        HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    private Map<String, Integer> buildHeaderIndex(String headerLine) {
        Map<String, Integer> headerIndex = new HashMap<>();
        List<String> headers = splitCsvLine(headerLine);
        for (int i = 0; i < headers.size(); i++) {
            headerIndex.put(normalizeHeader(headers.get(i)), i);
        }
        return headerIndex;
    }

    private String getCell(List<String> cells, Integer index) {
        if (index == null || index < 0 || index >= cells.size()) {
            return "";
        }
        return cells.get(index).trim();
    }

    private static String normalizeHeader(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\uFEFF", "")
                .replace("\"", "")
                .replaceAll("\\s+", "")
                .trim();
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim();
    }

    private String toGregorianYearMonth(String rocYearMonth) {
        if (rocYearMonth == null) {
            return null;
        }
        String normalized = rocYearMonth.replace("\"", "").trim();
        if (!normalized.matches("\\d{4,5}")) {
            return null;
        }

        try {
            int month = Integer.parseInt(normalized.substring(normalized.length() - 2));
            int rocYear = Integer.parseInt(normalized.substring(0, normalized.length() - 2));
            int year = rocYear + 1911;
            return String.format(Locale.ROOT, "%04d-%02d", year, month);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private double parseDouble(String text) {
        if (text == null) {
            return 0.0;
        }
        String normalized = text.replace("\"", "")
                .replace(",", "")
                .replace("+", "")
                .replace("%", "")
                .trim();
        if (normalized.isBlank() || "--".equals(normalized) || "-".equals(normalized)) {
            return 0.0;
        }
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private String cleanCellText(String text) {
        return text == null ? "" : text.replace("\u00A0", " ").trim();
    }

    private List<String> splitCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        cells.add(current.toString());
        return cells;
    }
}

