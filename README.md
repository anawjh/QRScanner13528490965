# 批量扫码工具 - Android App

## 功能特性

- ✅ **持续扫码模式** — 扫完一个自动打开下一个，无需手动点击
- ✅ **扫码记录列表** — 实时显示序号、二维码内容、扫描时间
- ✅ **备注/标签** — 每条记录可单独添加备注
- ✅ **删除记录** — 单条删除或一键清空
- ✅ **导出 Excel (.xlsx)** — 带样式的表格，含序号、内容、时间、备注
- ✅ **分享文件** — 导出后可直接分享到微信/邮件/钉钉等

---

## 编译步骤

### 环境要求
- Android Studio Hedgehog (2023.1.1) 或更新版本
- JDK 11+
- Android SDK 34

### 步骤

1. **打开项目**
   - 启动 Android Studio
   - File → Open → 选择 `QRScanner` 文件夹

2. **等待 Gradle 同步**
   - 首次打开会自动下载依赖（需联网）
   - 底部状态栏显示 "Gradle sync finished" 即完成

3. **连接安卓手机**
   - 手机开启「开发者模式」→「USB调试」
   - 用 USB 连接电脑

4. **运行/打包**
   - 直接运行：点击工具栏 ▶ 按钮（调试安装到手机）
   - 打包 APK：Build → Build Bundle(s)/APK(s) → Build APK(s)
   - APK 输出路径：`app/build/outputs/apk/debug/app-debug.apk`

---

## 项目结构

```
QRScanner/
├── app/src/main/
│   ├── java/com/qrscanner/
│   │   ├── MainActivity.java       # 主界面逻辑
│   │   ├── ScanRecord.java         # 数据模型
│   │   └── ScanRecordAdapter.java  # 列表适配器
│   └── res/
│       ├── layout/
│       │   ├── activity_main.xml   # 主界面布局
│       │   ├── item_scan_record.xml # 列表项布局
│       │   └── dialog_remark.xml   # 备注弹窗
│       └── xml/
│           └── file_paths.xml      # 文件分享配置
```

## 依赖库

| 库 | 用途 |
|---|---|
| `zxing-android-embedded` | 二维码扫描 |
| `poi-ooxml` | Excel 导出 |
| `material` | UI 组件 |

---

## Excel 导出格式

| 序号 | 二维码内容 | 扫描时间 | 备注/标签 |
|---|---|---|---|
| 1 | https://example.com | 2024-06-08 10:30:00 | 入库批次A |
| 2 | PRODUCT-001 | 2024-06-08 10:30:05 | |

- 表头蓝色加粗
- 数据行交替色
- 底部含导出时间摘要行
