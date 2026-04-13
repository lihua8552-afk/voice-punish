# Voice Punish

Fabric 1.21.11 朋友局语音监管附属模组。

作者: `AiannoToke`
作者 QQ: `2401742586`

## 功能

- 使用 `Simple Voice Chat` 的麦克风语音包做音量检测。
- 使用 `Shriek` 的中文转写文本做违禁词检测。
- 客户端启动时自动拉起 `vosk-model-small-cn-0.22` 中文模型。
- 只把转写文本回显给说话者自己。
- 命中超音量或违禁词后扣血，并附带随机惩罚事件。
- OP 可通过 `/voicepunish panel` 或 `/voicepunish editor` 打开游戏内设置面板，修改阈值、扣血量和随机事件概率。
- 提供 `/voicepunish` 管理指令和 `voice-punish.json5` 配置文件。

## 依赖

- `Simple Voice Chat 1.21.11-2.6.10`
- `Shriek 1.1.2+fabric`
- `Architectury API 19.0.1+fabric`
- `Fabric API 0.140.2+1.21.11`
- `Java 21+`

## 开发

```powershell
.\gradlew.bat build
```

产物位于 `build/libs/`。

## 首次启动

- `Shriek` 第一次会下载中文 Vosk 模型，可能需要等几十秒。
- 所有联机玩家都需要安装 `voice-punish`、`Simple Voice Chat`、`Shriek`、`Architectury API`。
- 日常交流继续走外部语音软件；游戏内语音只作为麦克风采集和检测链路。
