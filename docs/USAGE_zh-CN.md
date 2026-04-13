# Voice Punish 使用说明

作者: `AiannoToke`

作者 QQ: `2401742586`

## 模组用途

`Voice Punish` 是一个给 `Minecraft 1.21.11 Fabric` 朋友局使用的语音监管附属模组。

它的工作方式是：

- 用 `Simple Voice Chat` 负责选择麦克风并上传语音数据
- 用 `Shriek` 负责把中文语音转成文字
- 用 `Voice Punish` 做音量检测、违禁词检测、文本回显和处罚触发

这套方案适合 10 人内局域网或朋友联机，不是强对抗反作弊方案。

## 必装依赖

房主和所有玩家都需要安装以下模组：

- `Fabric API`
- `Architectury API`
- `Simple Voice Chat`
- `Shriek`
- `Voice Punish`

如果有人没装这些依赖，或者语音链路不完整，服务端会提示并可能把玩家踢出，以保证检测链路一致。

## 当前实例中的安装位置

当前整合包实例的模组目录：

- `versions/1.21.11生存rpg/mods`

当前违禁词配置文件：

- `versions/1.21.11生存rpg/config/voice-punish.json5`

源码工程目录：

- `voice-punish`

## 首次启动前要知道的事

- `Shriek` 第一次启动会自动下载中文语音模型 `vosk-model-small-cn-0.22`
- 第一次下载可能需要几十秒，网络慢时会更久
- 实际沟通仍然可以继续使用 QQ、KOOK、Discord 等外部语音软件
- 建议把游戏内 `Simple Voice Chat` 的播放音量调低或调成 `0`
- 但不要关掉 `Simple Voice Chat` 的麦克风链路，否则无法做音量检测

## 如何使用

### 1. 安装模组

把以下 jar 放到 `mods` 目录：

- `voice-punish-0.1.0.jar`
- `simple-voice-chat-fabric-1.21.11-2.6.10.jar`
- `shriek-1.1.2+fabric.jar`
- `architectury-fabric-19.0.1.jar`
- 你原本的 `fabric-api` 也要保留

### 2. 进入游戏后设置麦克风

- 打开 `Simple Voice Chat` 设置
- 选择正确的麦克风输入设备
- 建议保持语音激活模式
- 如果你只想让它检测、不想让大家在游戏内互相听见，可以把播放音量调成 `0`

### 3. OP 打开设置面板

游戏内 OP 可以使用：

- `/voicepunish panel`
- `/voicepunish editor`

面板里可以修改：

- 是否启用音量检测
- 是否启用文本检测
- 音量阈值
- 音量倍数
- 超阈值持续时间
- 超音量冷却时间
- 超音量扣血
- 超音量升级扣血
- 违禁词扣血
- 违禁词升级扣血
- 默认随机事件次数
- 升级随机事件次数
- 刷怪 / 删物品 / 负面效果 / 随机传送 的权重

面板里还提供一个 `违禁词设置` 子页面：

- 默认不会直接显示词库内容
- 需要先点 `查看违禁词` 才会正常显示并允许编辑
- 保存后会随主配置一起回写到 `voice-punish.json5`

点击“保存并应用”后会直接回写到服务端配置文件。

## 违禁词怎么改

违禁词既可以在游戏面板里改，也可以直接编辑配置文件：

- `versions/1.21.11生存rpg/config/voice-punish.json5`

编辑字段：

- `badWords`

这是一个字符串数组，你可以按下面的格式继续追加：

```json
"badWords": [
  "测试违禁词",
  "傻逼",
  "逆天",
  "你想新增的词"
]
```

改完后可以：

- 重启游戏，或
- 在游戏里执行 `/voicepunish reload`

## 当前默认词库包含什么

默认词库已经预置了三类：

### 1. 常见直接辱骂

- `傻逼`
- `脑残`
- `弱智`
- `智障`
- `废物`
- `人渣`
- `狗东西`
- `杂种`

### 2. 常见规避写法 / 缩写

- `傻b`
- `煞笔`
- `傻批`
- `伞兵`
- `沙比`
- `sb`
- `s13`
- `cnm`
- `nmsl`
- `tmd`

### 3. 近几年常见网络辱骂 / 烂梗化骂法

- `逆天`
- `真唐`
- `唐氏`
- `下头`
- `小丑`
- `狗叫`
- `臭鱼烂虾`
- `普信男`
- `普信女`
- `巨婴`
- `龟男`
- `捞女`

注意：

- 这些词里有一部分在某些语境下可能会误判
- 如果你们朋友局更想“严一点”，可以继续往里加
- 如果你们更想“少误伤”，可以删掉容易日常口头化的词

## 常用命令

- `/voicepunish panel`
  打开 OP 设置面板

- `/voicepunish editor`
  打开 OP 设置面板

- `/voicepunish reload`
  重载配置文件

- `/voicepunish status <player>`
  查看某个玩家当前语音状态

- `/voicepunish pardon <player>`
  清空某个玩家当前会话内的处罚累计

- `/voicepunish test loud [player]`
  测试超音量处罚

- `/voicepunish test badword [player]`
  测试违禁词处罚

- `/voicepunish test event [player]`
  测试随机事件处罚

- `/voicepunish selftext true|false`
  切换自己是否看到自己的语音转文字回显

## 处罚逻辑概览

默认逻辑是：

- 触发后先扣血
- 再随机触发一个事件

随机事件池包括：

- 随机刷一只敌对怪
- 随机删除一格物品
- 给予随机负面药水效果
- 安全随机传送

面板中改的是“权重”，不是直接百分比。

例如：

- 刷怪 30
- 删物品 25
- 负面效果 25
- 传送 20

系统会自动归一化成总概率 100%。

## 兼容与注意事项

- 这是朋友局模组，不建议拿去做高对抗大型服务器执法
- `Shriek` 依赖客户端语音转写，理论上可以被魔改客户端绕过
- 如果发现某些词误伤严重，优先从 `badWords` 里删掉
- 如果有人麦克风特别小声或特别吵，优先在面板里调 `音量阈值` 和 `音量倍数`
- 长时间运行时，模组会在玩家离开或服务器关闭时释放 Opus 解码器，并清理处罚时间队列，避免明显资源泄漏

## 推荐联机测试顺序

1. 先启动一次游戏，让 `Shriek` 下载中文模型
2. 进单人世界并“对局域网开放”
3. 先用 `/voicepunish test loud`
4. 再用 `/voicepunish test badword`
5. 确认扣血、刷怪、删物品、传送等事件正常
6. 再让朋友加入，测试真实语音链路

## 二次开发建议

如果后续还要继续扩展，可以优先从这几个位置改：

- `src/main/java/com/aiannotoke/voicepunish/service/VoiceModerationService.java`
- `src/main/java/com/aiannotoke/voicepunish/service/PunishmentEngine.java`
- `src/main/java/com/aiannotoke/voicepunish/config/VoicePunishConfig.java`
- `src/main/java/com/aiannotoke/voicepunish/client/gui/VoicePunishConfigScreen.java`

如果你后面还想继续加：

- 更细的违禁词分类
- 不同词对应不同处罚
- 黑白名单玩家
- 违禁词分组导入导出

都可以继续在当前结构上扩展。
