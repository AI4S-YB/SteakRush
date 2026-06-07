# SteakRush 0.2.0 Debug

## 更新概要

- 煎牛排底噪改用真实 CC0 煎烤音效，滋啦声比纯程序化噪声更自然。
- 保留程序化放肉入锅、拿起离锅短音效，增强操作反馈。
- 版本号更新为 `versionCode=2`、`versionName=0.2.0`。
- Debug APK 使用固定调试签名，后续同签名包可以覆盖安装。
- 安装脚本在遇到签名不一致时会提示卸载旧包和重装。

## 安装提示

如果设备上仍安装了早期不同签名的 `com.steakrush`，Android 会拒绝覆盖安装。先卸载一次旧包：

```powershell
adb uninstall com.steakrush
```

之后再安装本 Release 的 `SteakRush-debug.apk`。
