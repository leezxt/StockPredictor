package org.gtalent;

/**
 * 新聞資料模型。
 */
public class NewsData {
    private String date;
    private String stockId;
    private String title;
    private String content;
    private String source;
    private double sentimentPolarity;

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getStockId() {
        return stockId;
    }

    public void setStockId(String stockId) {
        this.stockId = stockId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public double getSentimentPolarity() {
        return sentimentPolarity;
    }

    public void setSentimentPolarity(double sentimentPolarity) {
        this.sentimentPolarity = sentimentPolarity;
    }
}

