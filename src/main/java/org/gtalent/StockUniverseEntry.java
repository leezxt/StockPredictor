package org.gtalent;

public class StockUniverseEntry {
    private final String symbol;
    private final String name;
    private final String market;
    private final String assetType;
    private final boolean etf;
    private final boolean active;

    public StockUniverseEntry(String symbol, String name, String market, String assetType, boolean etf, boolean active) {
        this.symbol = symbol == null ? "" : symbol.trim();
        this.name = name == null ? "" : name.trim();
        this.market = market == null ? "" : market.trim();
        this.assetType = assetType == null ? "UNKNOWN" : assetType.trim();
        this.etf = etf;
        this.active = active;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public String getMarket() {
        return market;
    }

    public String getAssetType() {
        return assetType;
    }

    public boolean isEtf() {
        return etf;
    }

    public boolean isActive() {
        return active;
    }
}

