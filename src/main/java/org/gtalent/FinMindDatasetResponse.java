package org.gtalent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FinMindDatasetResponse {
    private int status;
    private String msg;
    private List<FinMindChipData> data;

    public FinMindDatasetResponse() {
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public List<FinMindChipData> getData() {
        return data;
    }

    public void setData(List<FinMindChipData> data) {
        this.data = data;
    }
}
