# -*- mode: python ; coding: utf-8 -*-
"""Build the seller GUI into a single Windows exe.

    pyinstaller --clean --noconfirm build-license-gui.spec

Output: dist/QRScanner-激活码签发工具.exe

The private key is NOT bundled: it stays in %USERPROFILE%\.qrscanner-license and
is read at runtime. Never add it to datas.
"""
a = Analysis(
    ['license-gui.py'],
    pathex=[],
    binaries=[],
    datas=[('issue-license.py', '.')],
    hiddenimports=[],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
    optimize=0,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name='QRScanner-激活码签发工具',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)
