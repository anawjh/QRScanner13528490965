package com.qrscanner;

public class ScanRecord {
    private int seq;
    private String content;
    private String time;
    private String remark;

    public ScanRecord(int seq, String content, String time, String remark) {
        this.seq = seq;
        this.content = content;
        this.time = time;
        this.remark = remark;
    }

    public int getSeq() { return seq; }
    public void setSeq(int seq) { this.seq = seq; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
