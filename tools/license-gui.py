"""Windows GUI for signing QRScanner activation codes.

Wraps issue-license.py so selling does not need a terminal:

  paste machine code -> activate -> copy code -> send to the customer

Every issue is appended to a local log (issued.csv next to the keys) so you can
see what was sold, to whom, and re-send a code later. The private key is only
read from disk; it is never embedded in this program.

Build:  pyinstaller --onefile --windowed --name 激活码签发工具 license-gui.py
"""
import csv
import datetime
import importlib.util
import os
import sys
import tkinter as tk
from tkinter import messagebox, ttk

APP_TITLE = "QRScanner 激活码签发工具"
ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
MACHINE_CHARS = 24                # 15 bytes -> 24 base32 symbols


def _load_core():
    """Import issue-license.py by path: the filename has a dash in it."""
    here = getattr(sys, "_MEIPASS", os.path.dirname(os.path.abspath(__file__)))
    path = os.path.join(here, "issue-license.py")
    spec = importlib.util.spec_from_file_location("issue_license", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


core = _load_core()


class LicenseTool(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title(APP_TITLE)
        self.geometry("720x600")
        self.minsize(640, 520)
        self.configure(bg="#f5f7fa")

        self.key_ok = os.path.exists(core.PRIV_PATH)
        self.customer = tk.StringVar()
        self.contact = tk.StringVar()
        self.note = tk.StringVar()
        self.machine = tk.StringVar()
        self.result = tk.StringVar()

        self._build()
        self._check_key()

    # ------------------------------ ui ------------------------------

    def _build(self):
        style = ttk.Style(self)
        try:
            style.theme_use("vista")
        except tk.TclError:
            pass

        header = tk.Frame(self, bg="#1565c0")
        header.pack(fill="x")
        tk.Label(header, text=APP_TITLE, bg="#1565c0", fg="white",
                 font=("Microsoft YaHei UI", 15, "bold")).pack(
            side="left", padx=16, pady=12)

        self.lblKey = tk.Label(self, text="", bg="#f5f7fa", anchor="w",
                               font=("Microsoft YaHei UI", 9))
        self.lblKey.pack(fill="x", padx=16, pady=(10, 0))

        self.body = tk.Frame(self, bg="#f5f7fa")
        self.body.pack(fill="both", expand=True, padx=16, pady=12)

        self._build_issue_tab()
        self._build_history_tab()

    def _build_issue_tab(self):
        tab = tk.Frame(self.body, bg="#f5f7fa")
        tab.pack(fill="both", expand=True)

        f = tk.LabelFrame(tab, text="签发激活码", bg="#f5f7fa", font=("Microsoft YaHei UI", 10, "bold"),
                          padx=10, pady=10)
        f.pack(fill="x", pady=(0, 12))

        self._label(f, "客户机器码（24 位，设置→软件激活→复制机器码）")
        self.entry_machine = self._entry(f, self.machine)
        tk.Button(f, text="粘贴", width=8, command=self._paste_machine).pack(
            side="left", padx=(6, 0))
        tk.Button(f, text="清空", width=8, command=self.machine.set).pack(
            side="left", padx=(6, 0))

        self._label(f, "客户备注（可选，便于日后查找）")
        self.entry_customer = self._entry(f, self.customer)
        self._label(f, "联系方式（可选）")
        self.entry_contact = self._entry(f, self.contact)

        tk.Button(f, text="生成激活码", font=("Microsoft YaHei UI", 11, "bold"),
                  bg="#2e7d32", fg="white", relief="flat", height=1,
                  command=self._issue).pack(fill="x", pady=(14, 0))

        out = tk.LabelFrame(tab, text="激活码（发给客户）", bg="#f5f7fa",
                            font=("Microsoft YaHei UI", 10, "bold"),
                            padx=10, pady=10)
        out.pack(fill="both", expand=True)

        self.txtResult = tk.Text(out, height=4, wrap="char", bg="white",
                                 font=("Consolas", 11), relief="solid", borderwidth=1)
        self.txtResult.pack(fill="both", expand=True)
        self.txtResult.configure(state="disabled")

        row = tk.Frame(out, bg="#f5f7fa")
        row.pack(fill="x", pady=(8, 0))
        tk.Button(row, text="复制激活码", width=14, command=self._copy_result).pack(side="left")
        tk.Button(row, text="验签校验", width=12, command=self._verify_result).pack(
            side="left", padx=8)
        self.lblResult = tk.Label(row, text="", bg="#f5f7fa", anchor="w",
                                  font=("Microsoft YaHei UI", 9))
        self.lblResult.pack(side="left", padx=8)

    def _build_history_tab(self):
        bar = tk.Frame(self, bg="#f5f7fa")
        bar.pack(fill="x", padx=16, pady=(0, 6))
        tk.Label(bar, text="签发记录", bg="#f5f7fa", anchor="w",
                 font=("Microsoft YaHei UI", 11, "bold")).pack(side="left")
        tk.Button(bar, text="刷新", width=8, command=self._load_history).pack(side="right")
        tk.Button(bar, text="打开 CSV", width=10, command=self._open_csv).pack(
            side="right", padx=6)

        wrap = tk.Frame(self, bg="#f5f7fa")
        wrap.pack(fill="both", expand=True, padx=16, pady=(0, 14))
        cols = ("time", "machine", "customer", "contact")
        heads = ("时间", "机器码", "备注", "联系方式")
        self.tree = ttk.Treeview(wrap, columns=cols, show="headings", height=8)
        for col, head, width in zip(cols, heads, (140, 250, 130, 140)):
            self.tree.heading(col, text=head)
            self.tree.column(col, width=width, anchor="w")
        vs = ttk.Scrollbar(wrap, orient="vertical", command=self.tree.yview)
        self.tree.configure(yscrollcommand=vs.set)
        self.tree.pack(side="left", fill="both", expand=True)
        vs.pack(side="right", fill="y")

        self.txtHistory = tk.Text(self, height=6, wrap="char", bg="white",
                                  font=("Consolas", 8), state="disabled")
        self.txtHistory.pack(fill="x", padx=16, pady=(0, 14))

    @staticmethod
    def _label(parent, text):
        tk.Label(parent, text=text, bg="#f5f7fa", anchor="w",
                 font=("Microsoft YaHei UI", 9)).pack(
            fill="x", pady=(8, 3))

    @staticmethod
    def _entry(parent, var):
        e = tk.Entry(parent, textvariable=var, font=("Consolas", 11), relief="solid",
                     borderwidth=1)
        e.pack(fill="x")
        return e

    # ------------------------------ actions ------------------------------

    def _check_key(self):
        if self.key_ok:
            self.lblKey.config(text="私钥：%s" % core.PRIV_PATH, fg="#2e7d32")
        else:
            self.lblKey.config(
                text="未找到私钥：%s\n请把 license-private.key 放到该目录后再打开本程序。"
                     % core.PRIV_PATH, fg="#e53935")
            messagebox.showerror(APP_TITLE,
                                 "未找到私钥文件。\n\n请把 license-private.key 放到：\n%s\n\n"
                                 "也就是本程序所在的文件夹。" % core.KEY_DIR)
        self._load_history()

    def _paste_machine(self):
        try:
            text = self.clipboard_get()
        except tk.TclError:
            messagebox.showinfo(APP_TITLE, "剪贴板是空的。")
            return
        self.machine.set(text.strip())
        self._check_machine()

    def _check_machine(self):
        raw = core.ungroup(self.machine.get())
        if not raw:
            self.lblResult.config(text="", fg="#757575")
            return True
        if len(raw) != MACHINE_CHARS:
            self.lblResult.config(
                text="机器码应为 %d 位，当前 %d 位" % (MACHINE_CHARS, len(raw)), fg="#e53935")
            return False
        bad = set(raw) - set(ALPHABET)
        if bad:
            self.lblResult.config(
                text="含无效字符：%s（注意 I/O/0/1 不在字符表内）" % " ".join(sorted(bad)),
                fg="#e53935")
            return False
        self.lblResult.config(text="机器码格式正确", fg="#2e7d32")
        return True

    def _issue(self):
        code_text = self.machine.get().strip()
        if not code_text:
            messagebox.showwarning(APP_TITLE, "请先粘贴客户机器码。")
            return
        if not self._check_machine():
            messagebox.showwarning(APP_TITLE, "机器码格式有误，未生成任何激活码。")
            return
        if not self.key_ok:
            messagebox.showerror(APP_TITLE, "未找到私钥，无法签发。")
            return

        try:
            code = core.issue(code_text)
        except Exception as exc:                      # noqa: BLE001
            messagebox.showerror(APP_TITLE, "签发失败：\n%s" % exc)
            return

        machine = core.ungroup(code_text)
        self._append_log(machine, self.customer.get().strip(),
                         self.contact.get().strip())

        self.txtResult.configure(state="normal")
        self.txtResult.delete("1.0", "end")
        self.txtResult.insert("1.0", code)
        self.txtResult.configure(state="disabled")
        self.lblResult.config(text="已生成，请复制发给客户", fg="#2e7d32")
        self.txtResult.focus_set()
        self._load_history()
        self._copy_result()

    def _copy_result(self):
        text = self.txtResult.get("1.0", "end").strip()
        if not text:
            messagebox.showinfo(APP_TITLE, "还没有激活码。")
            return
        self.clipboard_clear()
        self.clipboard_append(text)
        self.update()
        self.lblResult.config(text="已复制到剪贴板", fg="#2e7d32")

    def _verify_result(self):
        text = self.txtResult.get("1.0", "end").strip()
        if not text:
            messagebox.showinfo(APP_TITLE, "还没有激活码。")
            return
        try:
            ok = core.verify(text)
        except Exception as exc:                      # noqa: BLE001
            messagebox.showerror(APP_TITLE, "校验出错：\n%s" % exc)
            return
        if ok:
            self.lblResult.config(text="签名有效", fg="#2e7d32")
        else:
            self.lblResult.config(text="签名无效（可能被改动或输错）", fg="#e53935")
            messagebox.showwarning(APP_TITLE, "签名无效。\n\n请检查是否复制完整、"
                                              "或是否被客户改动过。")

    # ------------------------------ log ------------------------------

    @property
    def log_path(self):
        return os.path.join(core.KEY_DIR, "issued.csv")

    def _append_log(self, machine, customer, contact):
        os.makedirs(core.KEY_DIR, exist_ok=True)
        exists = os.path.exists(self.log_path)
        with open(self.log_path, "a", newline="", encoding="utf-8-sig") as f:
            writer = csv.writer(f)
            if not exists:
                writer.writerow(["时间", "机器码", "备注", "联系方式"])
            writer.writerow([
                datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
                machine, customer, contact,
            ])

    def _load_history(self):
        if not os.path.exists(self.log_path):
            return
        for row in self.tree.get_children():
            self.tree.delete(row)
        self.txtHistory.configure(state="normal")
        self.txtHistory.delete("1.0", "end")
        with open(self.log_path, "r", newline="", encoding="utf-8-sig") as f:
            rows = list(csv.reader(f))
        for i, row in enumerate(rows[1:]):
            row = row + [""] * (4 - len(row))
            self.tree.insert("", "end", values=row[:4])
            if len(self.txtHistory.get("1.0", "end")) < 4000:
                try:
                    code = core.issue(row[1])
                    self.txtHistory.insert("end", "%s  %s\n" % (row[0], code))
                except Exception:                     # noqa: BLE001
                    pass
        self.txtHistory.configure(state="disabled")

    def _open_csv(self):
        if not os.path.exists(self.log_path):
            messagebox.showinfo(APP_TITLE, "还没有签发记录。")
            return
        os.startfile(os.path.dirname(self.log_path))


def main():
    app = LicenseTool()
    app.mainloop()


if __name__ == "__main__":
    main()
