package org.gtalent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * FinMind 融資融券 API 回傳包裝類別
 * Dataset: TaiwanStockMarginPurchaseShortSale
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FinMindMarginResponse {
    private int status;
    private String msg;
    private List<FinMindMarginData> data;

    public FinMindMarginResponse() {
    }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }

    public List<FinMindMarginData> getData() { return data; }
    public void setData(List<FinMindMarginData> data) { this.data = data; }
}

