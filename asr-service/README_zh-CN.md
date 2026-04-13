# Voice Punish ASR Service

这是给 `voice-punish` 模组配套的本地中文语音转写懒人服务包。

## 作用

- 在每个玩家自己的电脑上本地运行
- 模组把分段后的麦克风音频发给本机 `127.0.0.1:47831`
- 本服务使用 FunASR 中文模型返回更完整的中文句子
- 如果本服务没启动，模组会回退到内置的 Vosk / Shriek 转写链路

## 安装

1. 双击 `install.bat`
2. 等待自动安装运行环境、依赖、模型
3. 安装完成后服务会自动在后台启动
4. 之后开游戏即可用

安装器会自动把运行环境部署到：

- `%LOCALAPPDATA%\VoicePunishASR`

这样即使你的整合包目录有中文、空格，也不会影响本地服务运行。

模型缓存目录默认在：

- `%LOCALAPPDATA%\VoicePunishASR\model-cache`

如果你要手动启动或调试：

- 双击 `start-service.bat`

如果你要停止服务：

- 双击 `stop-service.bat`

如果你要卸载：

- 双击 `uninstall.bat`

## 配置

配置文件：

- `voice-punish-asr-service.json`

默认字段：

- `model`
- `vadModel`
- `puncModel`
- `device`
- `host`
- `port`
- `autoDownloadModels`

默认监听地址：

- `127.0.0.1:47831`

## 接口

- `GET /healthz`
- `GET /v1/info`
- `POST /v1/transcribe`

`POST /v1/transcribe` 直接接收 `audio/wav` 请求体，要求输入是 16k 单声道 WAV。

## 兼容性说明

- 安装器会自动下载本地 Miniforge 运行时，不依赖系统 Python
- Windows + NVIDIA GPU 下优先安装 GPU 版 PyTorch
- 如果 GPU 依赖安装失败，会自动回退到 CPU 版

## 故障排查

- 先访问 `http://127.0.0.1:47831/healthz`
- 日志文件在 `%LOCALAPPDATA%\VoicePunishASR\logs\service.log`
- 如果模型加载失败，删除 `%LOCALAPPDATA%\VoicePunishASR\model-cache` 后重新运行 `install.bat`
