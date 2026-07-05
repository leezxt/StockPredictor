package org.gtalent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class TwseService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TWSE_STOCK_DAY_URL = "https://www.twse.com.tw/exchangeReport/STOCK_DAY?response=json&date=%s&stockNo=%s";
    private static final String TWSE_OPENAPI_STOCK_DAY_ALL = "https://openapi.twse.com.tw/v1/exchangeReport/STOCK_DAY_ALL";
    private static final String TPEX_OPENAPI_MAINBOARD_DAILY_QUOTES = "https://www.tpex.org.tw/openapi/v1/tpex_mainboard_daily_close_quotes";
    private static final String FINMIND_STOCK_PRICE_URL = "https://api.finmindtrade.com/api/v4/data?dataset=TaiwanStockPrice&data_id=%s&start_date=%s";
    private static final String TWSE_T86_RWD_URL = "https://www.twse.com.tw/rwd/zh/fund/T86?response=json&date=%s&selectType=ALLBUT0999";
    private static final String TWSE_T86_LEGACY_URL = "https://www.twse.com.tw/fund/T86?response=json&date=%s&selectType=ALLBUT0999";
    private static final String TWSE_T86_AFTER_TRADING_URL = "https://www.twse.com.tw/rwd/zh/afterTrading/TWT86U?response=json&date=%s&selectType=ALLBUT0999";
    private static final Duration INSTITUTIONAL_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration INSTITUTIONAL_REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_CONSECUTIVE_INSTITUTIONAL_FAILURES = 6;
    private static final int DEFAULT_REQUEST_RETRIES = 2;
    private static final int INSTITUTIONAL_REQUEST_RETRIES = 3;

    public List<StockUniverseEntry> fetchMarketUniverse() {
        Map<String, StockUniverseEntry> dedup = new LinkedHashMap<>();
        mergeUniverseEntries(dedup, fetchTwseUniverseEntries());
        mergeUniverseEntries(dedup, fetchTpexUniverseEntries());
        return new ArrayList<>(dedup.values());
    }

    public int syncMarketUniverse() {
        List<StockUniverseEntry> universe = fetchMarketUniverse();
        if (universe.isEmpty()) {
            return 0;
        }
        return DatabaseManager.saveStockUniverse(universe);
    }

    private List<StockUniverseEntry> fetchTwseUniverseEntries() {
        return fetchUniverseFromJsonArray(
                TWSE_OPENAPI_STOCK_DAY_ALL,
                "Code",
                "Name",
                "TWSE"
        );
    }

    private List<StockUniverseEntry> fetchTpexUniverseEntries() {
        return fetchUniverseFromJsonArray(
                TPEX_OPENAPI_MAINBOARD_DAILY_QUOTES,
                "SecuritiesCompanyCode",
                "CompanyName",
                "OTC"
        );
    }

    private List<StockUniverseEntry> fetchUniverseFromJsonArray(String url, String symbolField, String nameField, String market) {
        List<StockUniverseEntry> results = new ArrayList<>();
        HttpClient client = HttpClient.newHttpClient();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            // 驗證響應是否為 JSON
            if (isHtmlResponse(body)) {
                System.err.println("股票池 API 返回 HTML 而非 JSON (" + market + ")");
                return results;
            }

            JsonNode root = new ObjectMapper().readTree(body);
            if (root == null || !root.isArray()) {
                return results;
            }

            Map<String, StockUniverseEntry> dedup = new LinkedHashMap<>();
            for (JsonNode row : root) {
                String code = row.path(symbolField).asText("").trim();
                if (code.isBlank() || !code.matches("^[0-9A-Z]{4,7}$")) {
                    continue;
                }
                String name = row.path(nameField).asText("").trim();
                StockClassification classification = classifyUniverseSymbol(code, name);
                dedup.put(code, new StockUniverseEntry(
                        code,
                        name,
                        market,
                        classification.assetType(),
                        classification.isEtf(),
                        classification.active()
                ));
            }
            results.addAll(dedup.values());
        } catch (Exception e) {
            System.err.println("同步股票池失敗(" + market + "): " + e.getMessage());
        }

        return results;
    }

    private void mergeUniverseEntries(Map<String, StockUniverseEntry> dedup, List<StockUniverseEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        for (StockUniverseEntry entry : entries) {
            if (entry == null || entry.getSymbol().isBlank()) {
                continue;
            }
            dedup.putIfAbsent(entry.getSymbol(), entry);
        }
    }

    private StockClassification classifyUniverseSymbol(String symbol, String name) {
        String code = symbol == null ? "" : symbol.trim().toUpperCase();
        String cleanName = name == null ? "" : name.trim();

        if (code.matches("^02\\d{3,5}$")) {
            return new StockClassification("ETN", false, true);
        }

        if (code.matches("^00[0-9A-Z]{2,5}$")) {
            boolean bondEtf = code.endsWith("B") || cleanName.contains("債");
            return new StockClassification(bondEtf ? "BOND_ETF" : "ETF", true, true);
        }

        if (code.matches("^\\d{4}$")) {
            return new StockClassification("STOCK", false, true);
        }

        return new StockClassification("EXCLUDED", false, false);
    }

    private record StockClassification(String assetType, boolean isEtf, boolean active) {}

    public List<String[]> fetchMonthlyData(String stockNo, String date) {
        List<String[]> results = new ArrayList<>();
        String url = String.format(TWSE_STOCK_DAY_URL, date, stockNo);

        try {
            JsonHttpResponse jsonResponse = fetchJsonResponse(url, Duration.ofSeconds(8), DEFAULT_REQUEST_RETRIES, "STOCK_DAY");
            if (jsonResponse == null) {
                System.err.println("證交所 API 無法返回有效 JSON (stockNo=" + stockNo + ", date=" + date + ")");
                return results;
            }

            JsonNode dataNode = OBJECT_MAPPER.readTree(jsonResponse.body()).get("data");

            if (dataNode != null && dataNode.isArray()) {
                for (JsonNode row : dataNode) {
                    if (!row.isArray() || row.size() < 7) {
                        continue;
                    }
                    // 回傳至少到收盤價(6)，供 DatabaseManager 解析日期/成交量/收盤價
                    results.add(new String[]{
                            row.get(0).asText(),
                            row.get(1).asText(),
                            row.get(2).asText(),
                            row.get(3).asText(),
                            row.get(4).asText(),
                            row.get(5).asText(),
                            row.get(6).asText()
                    });
                }
            }
        } catch (Exception e) {
            System.err.println("證交所抓取錯誤: " + e.getMessage());
        }

        if (results.isEmpty()) {
            results.addAll(fetchMonthlyDataFromFinMind(stockNo, date));
        }
        return results;
    }

    private List<String[]> fetchMonthlyDataFromFinMind(String stockNo, String twseDate) {
        List<String[]> results = new ArrayList<>();
        String startDate = toWesternMonthStart(twseDate);
        if (stockNo == null || stockNo.isBlank() || startDate.isBlank()) {
            return results;
        }

        String targetMonth = startDate.substring(0, 7);
        String url = String.format(FINMIND_STOCK_PRICE_URL, stockNo, startDate);
        try {
            JsonHttpResponse jsonResponse = fetchJsonResponse(url, Duration.ofSeconds(8), DEFAULT_REQUEST_RETRIES, "FinMindStockPrice");
            if (jsonResponse == null) {
                return results;
            }

            JsonNode dataNode = OBJECT_MAPPER.readTree(jsonResponse.body()).path("data");
            if (!dataNode.isArray()) {
                return results;
            }

            for (JsonNode row : dataNode) {
                String rowDate = row.path("date").asText("");
                if (!rowDate.startsWith(targetMonth)) {
                    continue;
                }
                String close = cleanPrice(row.path("close").asText(""));
                if (close.isBlank()) {
                    continue;
                }
                results.add(new String[]{
                        toMinguoDate(rowDate),
                        cleanVolume(row.path("Trading_Volume").asText("")),
                        row.path("Trading_money").asText("0"),
                        cleanPrice(row.path("open").asText(close)),
                        cleanPrice(row.path("max").asText(close)),
                        cleanPrice(row.path("min").asText(close)),
                        close
                });
            }

            if (!results.isEmpty()) {
                System.out.printf("[TwseService] FinMind 備援取得 %s %s 月資料 %d 筆%n", stockNo, targetMonth, results.size());
            }
        } catch (Exception e) {
            System.err.println("FinMind 股價備援抓取錯誤: " + e.getMessage());
        }
        return results;
    }

    private String toWesternMonthStart(String twseDate) {
        if (twseDate == null || !twseDate.matches("\\d{8}")) {
            return "";
        }
        return twseDate.substring(0, 4) + "-" + twseDate.substring(4, 6) + "-01";
    }

    private String toMinguoDate(String westernDate) {
        if (westernDate == null || !westernDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return westernDate == null ? "" : westernDate;
        }
        int year = Integer.parseInt(westernDate.substring(0, 4)) - 1911;
        return year + "/" + westernDate.substring(5, 7) + "/" + westernDate.substring(8, 10);
    }

    private String cleanVolume(String raw) {
        if (raw == null) {
            return "0";
        }
        String value = raw.replace(",", "").trim();
        return value.matches("\\d+") ? value : "0";
    }

    public List<InstitutionalTrade> fetchRecentInstitutionalData(String stockNo, int days) {
        List<InstitutionalTrade> trades = new ArrayList<>();
        if (stockNo == null || stockNo.isBlank() || days <= 0) {
            return trades;
        }

        LocalDate cursor = LocalDate.now();
        int attempts = 0;
        int maxAttempts = Math.max(days * 3, 30); // 市場休市容錯
        int consecutiveFailures = 0;

        while (trades.size() < days && attempts < maxAttempts) {
            attempts++;
            InstitutionalTrade oneDay = fetchInstitutionalDataByDate(stockNo, cursor);
            if (oneDay != null) {
                trades.add(0, oneDay); // 保持舊 -> 新
                consecutiveFailures = 0;
            } else {
                consecutiveFailures++;
                if (consecutiveFailures >= MAX_CONSECUTIVE_INSTITUTIONAL_FAILURES) {
                    // 長時間連續失敗時快速返回，避免整體 API 等到前端逾時。
                    break;
                }
            }
            cursor = cursor.minusDays(1);
        }

        return trades;
    }

    public InstitutionalTrade fetchInstitutionalDataByDate(String stockNo, LocalDate date) {
        if (stockNo == null || stockNo.isBlank() || date == null) {
            return null;
        }

        String dateStr = date.format(DateTimeFormatter.BASIC_ISO_DATE);
        String[] candidateUrls = {
                String.format(TWSE_T86_RWD_URL, dateStr),
                String.format(TWSE_T86_LEGACY_URL, dateStr),
                String.format(TWSE_T86_AFTER_TRADING_URL, dateStr)
        };

        for (String url : candidateUrls) {
            InstitutionalTrade trade = fetchInstitutionalDataFromUrl(stockNo, date, url);
            if (trade != null) {
                return trade;
            }
        }

        return null;
    }

    private InstitutionalTrade fetchInstitutionalDataFromUrl(String stockNo, LocalDate date, String url) {
        try {
            JsonHttpResponse jsonResponse = fetchJsonResponse(url, INSTITUTIONAL_REQUEST_TIMEOUT, INSTITUTIONAL_REQUEST_RETRIES, "T86");
            if (jsonResponse == null) {
                return null;
            }

            return parseInstitutionalPayload(stockNo, date, jsonResponse.body());
        } catch (Exception e) {
            System.err.println("抓取法人資料失敗(" + stockNo + ", " + date + ", url=" + url + "): " + e.getMessage());
            return null;
        }
    }

    private InstitutionalTrade parseInstitutionalPayload(String stockNo, LocalDate date, String body) {
        if (body == null || body.isBlank()) {
            return null;
        }

        try {
            if (body.contains("\"status\"") && body.contains("\"msg\"") && body.contains("\"data\"")) {
                FinMindInstitutionalInvestorsBuySellResponse finMindResponse = parseFinMindInstitutionalInvestorsBuySellResponse(body);
                if (finMindResponse != null && finMindResponse.getData() != null && !finMindResponse.getData().isEmpty()) {
                    System.out.println("FinMind 法人資料已解析，筆數=" + finMindResponse.getData().size());
                }
            }

            JsonNode root = new ObjectMapper().readTree(body);

            if (root == null) {
                return null;
            }

            if (root.isArray()) {
                for (JsonNode row : root) {
                    InstitutionalTrade trade = parseInstitutionalRowObject(stockNo, date, row);
                    if (trade != null) {
                        return trade;
                    }
                }
                return null;
            }

            JsonNode dataNode = root.path("data");
            JsonNode fieldsNode = root.path("fields");

            if (dataNode.isArray() && !dataNode.isEmpty()) {
                if (fieldsNode.isArray()) {
                    int symbolIdx = findFieldIndex(fieldsNode, "證券代號");
                    int foreignNetIdx = findAnyFieldIndex(fieldsNode,
                            "外陸資買賣超股數(不含外資自營商)",
                            "外資及陸資買賣超股數(不含外資自營商)",
                            "外資及陸資買賣超股數");
                    int trustNetIdx = findAnyFieldIndex(fieldsNode, "投信買賣超股數");
                    int dealerNetIdx = findAnyFieldIndex(fieldsNode,
                            "自營商買賣超股數",
                            "自營商(自行買賣)買賣超股數",
                            "自營商(避險)買賣超股數");

                    if (symbolIdx >= 0 && foreignNetIdx >= 0 && trustNetIdx >= 0 && dealerNetIdx >= 0) {
                        for (JsonNode row : dataNode) {
                            if (!row.isArray()) {
                                continue;
                            }

                            String symbol = safeCell(row, symbolIdx);
                            if (!stockNo.equals(symbol)) {
                                continue;
                            }

                            long foreign = parseLongCell(safeCell(row, foreignNetIdx));
                            long trust = parseLongCell(safeCell(row, trustNetIdx));
                            long dealer = parseLongCell(safeCell(row, dealerNetIdx));
                            return new InstitutionalTrade(date.toString(), foreign, trust, dealer);
                        }
                    }
                }

                for (JsonNode row : dataNode) {
                    InstitutionalTrade trade = parseInstitutionalRowObject(stockNo, date, row);
                    if (trade != null) {
                        return trade;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("解析法人資料失敗(" + stockNo + ", " + date + "): " + e.getMessage());
        }

        return null;
    }

    private InstitutionalTrade parseInstitutionalRowObject(String stockNo, LocalDate date, JsonNode row) {
        if (row == null || !row.isObject()) {
            return null;
        }

        String symbol = readFirstText(row, "證券代號", "股票代號", "Code", "Symbol");
        if (!stockNo.equals(symbol)) {
            return null;
        }

        long foreign = parseLongCell(readFirstText(row,
                "外陸資買賣超股數(不含外資自營商)",
                "外資及陸資買賣超股數(不含外資自營商)",
                "外資及陸資買賣超股數",
                "ForeignBuy"));
        long trust = parseLongCell(readFirstText(row, "投信買賣超股數", "TrustBuy", "InvestmentTrust"));
        long dealer = parseLongCell(readFirstText(row,
                "自營商買賣超股數",
                "自營商(自行買賣)買賣超股數",
                "自營商(避險)買賣超股數",
                "DealerBuy"));
        return new InstitutionalTrade(date.toString(), foreign, trust, dealer);
    }

    private String readFirstText(JsonNode node, String... fieldNames) {
        if (node == null || !node.isObject()) {
            return "";
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && !value.isNull()) {
                String text = value.asText("").trim();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }

    public FinMindInstitutionalInvestorsBuySellResponse parseFinMindInstitutionalInvestorsBuySellResponse(String body) {
        if (body == null || body.isBlank() || isHtmlResponse(body)) {
            return null;
        }

        try {
            return new ObjectMapper().readValue(body, FinMindInstitutionalInvestorsBuySellResponse.class);
        } catch (Exception e) {
            System.err.println("解析 FinMind 法人資料失敗: " + e.getMessage());
            return null;
        }
    }

    private int findFieldIndex(JsonNode fieldsNode, String fieldName) {
        for (int i = 0; i < fieldsNode.size(); i++) {
            if (fieldName.equals(fieldsNode.get(i).asText())) {
                return i;
            }
        }
        return -1;
    }

    private int findAnyFieldIndex(JsonNode fieldsNode, String... candidates) {
        for (String name : candidates) {
            int idx = findFieldIndex(fieldsNode, name);
            if (idx >= 0) {
                return idx;
            }
        }
        return -1;
    }

    private String safeCell(JsonNode row, int idx) {
        if (idx < 0 || idx >= row.size()) {
            return "";
        }
        return row.get(idx).asText("").trim();
    }

    private long parseLongCell(String text) {
        if (text == null || text.isBlank() || "--".equals(text)) {
            return 0L;
        }
        String normalized = text.replace(",", "").replace("+", "").trim();
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    public String fetchStockName(String stockNo) {
        if (stockNo == null || stockNo.isBlank()) {
            return "";
        }

        HttpClient client = HttpClient.newHttpClient();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TWSE_OPENAPI_STOCK_DAY_ALL))
                    .header("User-Agent", "Mozilla/5.0")
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            // 驗證響應是否為 JSON
            if (isHtmlResponse(body)) {
                System.err.println("OpenAPI 返回 HTML 而非 JSON");
                return "";
            }

            JsonNode root = new ObjectMapper().readTree(body);
            if (root != null && root.isArray()) {
                for (JsonNode row : root) {
                    if (stockNo.equals(row.path("Code").asText())) {
                        String name = row.path("Name").asText("").trim();
                        if (!name.isBlank()) {
                            return name;
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("查詢股票名稱(OpenAPI)失敗: " + e.getMessage());
        }

        Calendar cal = Calendar.getInstance();

        // Try current month first; if title is empty, fallback to previous month.
        for (int i = 0; i < 2; i++) {
            String date = String.format("%04d%02d01", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1);
            String url = String.format(TWSE_STOCK_DAY_URL, date, stockNo);

            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", "Mozilla/5.0")
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                String body = response.body();

                // 驗證響應是否為 JSON
                if (isHtmlResponse(body)) {
                    System.err.println("STOCK_DAY API 返回 HTML 而非 JSON");
                    cal.add(Calendar.MONTH, -1);
                    continue;
                }

                JsonNode root = new ObjectMapper().readTree(body);
                String title = root.path("title").asText("");
                String parsedName = parseStockNameFromTitle(stockNo, title);
                if (!parsedName.isBlank()) {
                    return parsedName;
                }
            } catch (Exception e) {
                System.err.println("查詢股票名稱(STOCK_DAY)失敗: " + e.getMessage());
            }

            cal.add(Calendar.MONTH, -1);
        }

        return "";
    }

    private String parseStockNameFromTitle(String stockNo, String title) {
        if (title == null || title.isBlank()) {
            return "";
        }

        int idx = title.indexOf(stockNo);
        if (idx < 0) {
            return "";
        }

        String afterCode = title.substring(idx + stockNo.length());
        afterCode = afterCode.replace("各日成交資訊", "").trim();
        if (afterCode.isBlank()) {
            return "";
        }

        String normalized = afterCode.replaceAll("\\s+", " ");
        return normalized.split(" ")[0].trim();
    }

    public void fetchYearlyData(String stockNo, int startYear, int startMonth) {
        // 取得當前時間
        Calendar cal = Calendar.getInstance();
        int currentYear = cal.get(Calendar.YEAR);
        int currentMonth = cal.get(Calendar.MONTH) + 1;

        int tempYear = startYear;
        int tempMonth = startMonth;

        // 循環抓取，直到抓到當前月份
        while (tempYear < currentYear || (tempYear == currentYear && tempMonth <= currentMonth)) {
            // 格式化日期為 YYYYMM01 (證交所 API 要求)
            String dateStr = String.format("%d%02d01", tempYear, tempMonth);
            System.out.println("正在下載 " + tempYear + " 年 " + tempMonth + " 月的資料...");

            // 執行之前的抓取邏輯並存入資料庫
            List<String[]> monthData = fetchMonthlyData(stockNo, dateStr);
            int saved = saveAllToDatabase(stockNo, monthData);
            System.out.println("本月已寫入 " + saved + " 筆");

            // 每抓完一個月休息，避免被限流
            try {
                System.out.println("休息一下，避免被限流...");
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("抓取流程被中斷");
                return;
            }

            // 月份遞增邏輯
            tempMonth++;
            if (tempMonth > 12) {
                tempMonth = 1;
                tempYear++;
            }
        }
    }

    private int saveAllToDatabase(String stockNo, List<String[]> monthData) {
        if (monthData == null || monthData.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (String[] dayData : monthData) {
            if (dayData == null || dayData.length < 2) {
                continue;
            }

            String westernDate = convertToWesternDate(dayData[0]);
            if (westernDate == null) {
                continue;
            }

            String price;
            if (dayData.length >= 7) {
                price = dayData[6] == null ? "" : dayData[6].replace(",", "");
            } else {
                price = dayData[1] == null ? "" : dayData[1].replace(",", "");
            }
            if (price.isBlank()) {
                continue;
            }

            if (DatabaseManager.saveSimpleData(stockNo, westernDate, price)) {
                count++;
            }
        }
        return count;
    }

    private String convertToWesternDate(String twDate) {
        if (twDate == null || twDate.isBlank()) {
            return null;
        }

        String[] parts = twDate.split("/");
        if (parts.length != 3) {
            return null;
        }

        try {
            int westernYear = Integer.parseInt(parts[0]) + 1911;
            return westernYear + "-" + parts[1] + "-" + parts[2];
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 全市場當日批次更新
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 使用 TWSE STOCK_DAY_ALL OpenAPI 及 TPEX daily quotes API，
     * 一次性抓取所有上市 / 上櫃股票今日收盤資料並寫入 DB。
     *
     * @return 寫入的總資料列數
     */
    public int bulkFetchAndSaveTodayData() {
        int total = 0;
        total += bulkFetchTwseTodayData();
        total += bulkFetchTpexTodayData();
        System.out.printf("[TwseService] 全市場今日資料更新完成：共寫入 %d 筆%n", total);
        return total;
    }

    /** 抓 TWSE STOCK_DAY_ALL，解析後批次寫入 */
    private int bulkFetchTwseTodayData() {
        HttpClient client = HttpClient.newHttpClient();
        String todayStr = LocalDate.now().toString(); // yyyy-MM-dd
        int count = 0;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TWSE_OPENAPI_STOCK_DAY_ALL))
                    .header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            // 驗證響應是否為 JSON
            if (isHtmlResponse(body)) {
                System.err.println("[TwseService] STOCK_DAY_ALL API 返回 HTML 而非 JSON");
                return 0;
            }

            JsonNode root = new ObjectMapper().readTree(body);
            if (root == null || !root.isArray()) {
                return 0;
            }
            for (JsonNode row : root) {
                String code = row.path("Code").asText("").trim();
                if (code.isBlank()) continue;

                String open  = cleanPrice(row.path("OpeningPrice").asText(""));
                String high  = cleanPrice(row.path("HighestPrice").asText(""));
                String low   = cleanPrice(row.path("LowestPrice").asText(""));
                String close = cleanPrice(row.path("ClosingPrice").asText(""));
                if (close.isBlank()) continue;

                String vol = row.path("TradeVolume").asText("0").replace(",", "").trim();
                if (vol.isBlank() || !vol.matches("[0-9]+")) vol = "0";

                if (open.isBlank())  open  = close;
                if (high.isBlank())  high  = close;
                if (low.isBlank())   low   = close;

                // 取得 API 回傳的日期欄位（格式 YYYYMMDD 或 YYYY/MM/DD）
                String dateField = row.path("Date").asText("").trim();
                String date = parseBulkDate(dateField, todayStr);

                DatabaseManager.saveBulkOhlcvRow(code, date, open, high, low, close, vol);
                count++;
            }
        } catch (Exception e) {
            System.err.println("[TwseService] STOCK_DAY_ALL 批次更新失敗: " + e.getMessage());
        }
        return count;
    }

    /** 抓 TPEX mainboard daily quotes，解析後批次寫入 */
    private int bulkFetchTpexTodayData() {
        HttpClient client = HttpClient.newHttpClient();
        String todayStr = LocalDate.now().toString();
        int count = 0;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TPEX_OPENAPI_MAINBOARD_DAILY_QUOTES))
                    .header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            // 驗證響應是否為 JSON
            if (isHtmlResponse(body)) {
                System.err.println("[TwseService] TPEX API 返回 HTML 而非 JSON");
                return 0;
            }

            JsonNode root = new ObjectMapper().readTree(body);
            if (root == null || !root.isArray()) {
                return 0;
            }
            for (JsonNode row : root) {
                String code = row.path("SecuritiesCompanyCode").asText("").trim();
                if (code.isBlank()) continue;

                String close = cleanPrice(row.path("Close").asText(""));
                if (close.isBlank()) continue;

                String open  = cleanPrice(row.path("Open").asText(""));
                String high  = cleanPrice(row.path("High").asText(""));
                String low   = cleanPrice(row.path("Low").asText(""));
                if (open.isBlank())  open  = close;
                if (high.isBlank())  high  = close;
                if (low.isBlank())   low   = close;

                // TPEX volume is in shares (張 * 1000), some APIs return 千股 or 股
                String vol = row.path("Volume").asText("0").replace(",", "").trim();
                if (vol.isBlank() || !vol.matches("[0-9]+")) vol = "0";

                String dateField = row.path("Date").asText("").trim();
                String date = parseBulkDate(dateField, todayStr);

                DatabaseManager.saveBulkOhlcvRow(code, date, open, high, low, close, vol);
                count++;
            }
        } catch (Exception e) {
            System.err.println("[TwseService] TPEX daily 批次更新失敗: " + e.getMessage());
        }
        return count;
    }

    private static String cleanPrice(String raw) {
        if (raw == null) return "";
        String s = raw.replace(",", "").trim();
        return (s.isEmpty() || "--".equals(s)) ? "" : s;
    }

    /** 請求 JSON 並驗證狀態碼、Content-Type 與回應內容，避免誤把 HTML 當成資料。 */
    private JsonHttpResponse fetchJsonResponse(String url, Duration requestTimeout, int maxAttempts, String sourceTag) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(INSTITUTIONAL_CONNECT_TIMEOUT)
                .build();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String requestUrl = appendCacheBuster(url, attempt);
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(requestUrl))
                        .timeout(requestTimeout)
                        .header("User-Agent", "Mozilla/5.0")
                        .header("Referer", "https://www.twse.com.tw/")
                        .header("Accept", "application/json, text/plain, */*")
                        .header("Cache-Control", "no-cache")
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                int statusCode = response.statusCode();
                String contentType = response.headers().firstValue("Content-Type").orElse("");
                String body = response.body();

                if (statusCode >= 200 && statusCode < 300 && isValidJsonPayload(contentType, body)) {
                    return new JsonHttpResponse(body, statusCode, contentType);
                }

                System.err.println("[" + sourceTag + "] 第 " + attempt + " 次請求非 JSON，status="
                        + statusCode + ", contentType=" + contentType + ", sample=" + sampleBody(body));
            } catch (Exception e) {
                System.err.println("[" + sourceTag + "] 第 " + attempt + " 次請求失敗: " + e.getMessage());
            }

            if (attempt < maxAttempts) {
                try {
                    Thread.sleep(250L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }

        return null;
    }

    private String appendCacheBuster(String url, int attempt) {
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + "_ts=" + System.currentTimeMillis() + "_" + attempt;
    }

    private boolean isValidJsonPayload(String contentType, String body) {
        if (body == null || body.isBlank() || isHtmlResponse(body)) {
            return false;
        }

        String normalizedContentType = contentType == null ? "" : contentType.toLowerCase();
        if (normalizedContentType.contains("json")) {
            return true;
        }

        int i = 0;
        while (i < body.length() && (body.charAt(i) <= ' ' || body.charAt(i) == '\uFEFF')) {
            i++;
        }
        if (i >= body.length()) {
            return false;
        }
        char first = body.charAt(i);
        return first == '{' || first == '[';
    }

    private String sampleBody(String body) {
        if (body == null) {
            return "<null>";
        }
        String compact = body.replaceAll("\\s+", " ").trim();
        if (compact.isBlank()) {
            return "<empty>";
        }
        int maxLen = 80;
        return compact.length() <= maxLen ? compact : compact.substring(0, maxLen) + "...";
    }

    private record JsonHttpResponse(String body, int statusCode, String contentType) {}

    /** 判斷 HTTP 回應是否為 HTML（含 BOM 前綴處理）。JSON 永遠不以 '<' 開頭。 */
    private static boolean isHtmlResponse(String body) {
        if (body == null) return true;
        int i = 0;
        while (i < body.length() && (body.charAt(i) <= ' ' || body.charAt(i) == '\uFEFF')) i++;
        return i < body.length() && body.charAt(i) == '<';
    }

    private static String parseBulkDate(String dateField, String fallback) {
        if (dateField == null || dateField.isBlank()) return fallback;
        // 格式 1: 20260519
        if (dateField.matches("\\d{8}")) {
            return dateField.substring(0, 4) + "-" + dateField.substring(4, 6) + "-" + dateField.substring(6);
        }
        // 格式 2: 2026/05/19 or 2026-05-19
        if (dateField.matches("\\d{4}[/\\-]\\d{2}[/\\-]\\d{2}")) {
            return dateField.replace("/", "-");
        }
        // 格式 3: 115/05/19 (民國年)
        if (dateField.matches("\\d{3}/\\d{2}/\\d{2}")) {
            String[] p = dateField.split("/");
            return (Integer.parseInt(p[0]) + 1911) + "-" + p[1] + "-" + p[2];
        }
        return fallback;
    }
}
