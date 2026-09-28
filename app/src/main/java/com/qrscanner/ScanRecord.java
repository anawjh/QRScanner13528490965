package com.qrscanner;

public class ScanRecord {
    private int seq;
    private String content;
    private String time;
    private String remark;
    private String format;
    private boolean blocked;
    private String imagePath;

    public ScanRecord(int seq, String content, String time, String remark) {
        this(seq, content, time, remark, "", false, "");
    }

    public ScanRecord(int seq, String content, String time, String remark,
                      String format, boolean blocked, String imagePath) {
        this.seq = seq;
        this.content = content;
        this.time = time;
        this.remark = remark;
        this.format = format;
        this.blocked = blocked;
        this.imagePath = imagePath;
    }

    public int getSeq() { return seq; }
    public void setSeq(int seq) { this.seq = seq; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public boolean isBlocked() { return blocked; }
    public void setBlocked(boolean blocked) { this.blocked = blocked; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
}
