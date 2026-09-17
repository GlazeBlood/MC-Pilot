# MCpilot

**让 AI agent 看见并操作你的 Minecraft 客户端。**

[English](README.md) | **中文**

MCpilot 由一个很小的 Fabric 模组和一个 agent skill 组成，二者配合把本地 Minecraft 1.20.6
客户端通过一个**仅限本机回环**的 HTTP 桥暴露出来。AI agent 因此可以截图、读取结构化游戏状态、
注入键盘/鼠标/聊天输入 —— 真正去*玩*、去*测*、去*验证*游戏，而不是靠猜日志。

> 所有输入都走原版同一套代码路径（和真人敲键盘、动鼠标完全一致），没有读取世界数据的作弊通道，
> 也没有任何超出"玩家本人能做到"的能力。HTTP 服务**只绑定 `127.0.0.1`**。

---

## 为什么做这个

自动化 MC 客户端通常只有两条路：要么是无头机器人（从不碰真实客户端），要么是脆弱的截图识别。
MCpilot 跑在真实客户端内部，于是：

- **agent 看到的和你看到的一样** —— 真实帧缓冲，包含 HUD 和任何已打开的界面。
- **agent 拿到的数字可信** —— 坐标、血量、背包、准星指向、服务端界面内容都以结构化 JSON 返回，
  不用从像素里猜。
- **验证是真的** —— 本项目最初的用途就是逐项核对服务端 GUI（DeluxeMenus / TrMenu / ItemsAdder
  菜单）与设计稿是否一致，包括 `CustomModelData` 图标绑定。

## 仓库结构

```
mc-pilot/
├── mod/                  Fabric 模组 "aipilot"（游戏内的 HTTP 桥）
│   └── src/main/java/dev/aipilot/
│       ├── AiPilotClient.java     模组入口，读取 -Daipilot.port / -Daipilot.token
│       ├── PilotServer.java       HTTP 服务与路由（仅回环）
│       ├── Actions.java           一个端点一个方法，全部在游戏主线程执行
│       ├── VirtualPointer.java    真人用鼠标也不会被打断的 GUI 虚拟指针
│       ├── KeyNames.java          按键名 → GLFW 键码表
│       └── mixin/                 最小化的 accessor / invoker
├── skill/                Agent skill（放进你的 agent 技能目录）
│   ├── SKILL.md          agent 阅读的"感知—行动"循环
│   └── scripts/mc.ps1    基于桥的 PowerShell 命令行工具
└── docs/
    ├── API.md            完整端点参考
    └── VIRTUAL-POINTER.md 虚拟指针设计说明
```

## 环境要求

| | |
|---|---|
| Minecraft | 1.20.6，**客户端** |
| 加载器 | Fabric Loader ≥ 0.15.0 |
| Java | 21+ |
| 系统 | skill 的命令行工具面向 Windows（PowerShell）；模组本身跨平台 |

## 构建

```bash
cd mod
./gradlew build          # 或者：gradle build
```

产物在 `mod/build/libs/aipilot-<版本>.jar`。

> Fabric Loom 首次运行需要下载并 remap Minecraft，因此第一次构建要几分钟且需要联网；之后再构建很快。
> 也可以直接到 [Releases](https://github.com/GlazeBlood/MC-Pilot/releases) 下载现成的 jar。

## 安装

1. 安装 **Minecraft 1.20.6 + Fabric Loader**（PCL2：版本列表 → 安装新版本 → 1.20.6，选择 Fabric）。
2. 把 `aipilot-<版本>.jar` 放进该版本的 `mods\` 文件夹（没有就新建）。
3. 可选 JVM 参数（启动器 → 版本设置 → JVM 参数）：

   | 参数 | 作用 |
   |---|---|
   | `-Daipilot.port=8765` | 换端口 |
   | `-Daipilot.token=你的密钥` | 之后每个请求都需带 `X-Ai-Pilot-Token` 头 |

4. 启动游戏。日志出现下面这行即成功：

   ```
   [aipilot] HTTP bridge listening on http://127.0.0.1:8765 (no token; localhost only)
   ```

## 使用

### 浏览器或 curl

```bash
curl http://127.0.0.1:8765/health
curl http://127.0.0.1:8765/state
curl -o shot.png http://127.0.0.1:8765/screenshot
curl -X POST http://127.0.0.1:8765/look -d '{"yaw":90,"pitch":0}'
curl -X POST http://127.0.0.1:8765/chat -d '{"message":"/time set day"}'
```

### skill 命令行工具

```powershell
$mc = ".\skill\scripts\mc.ps1"   # ExecutionPolicy 可能要求加 -ExecutionPolicy Bypass

powershell.exe -NoProfile -ExecutionPolicy Bypass -File $mc health
powershell.exe -NoProfile -ExecutionPolicy Bypass -File $mc shot .\shots\mc.png
powershell.exe -NoProfile -ExecutionPolicy Bypass -File $mc state
powershell.exe -NoProfile -ExecutionPolicy Bypass -File $mc clickat 426 285 left
```

### 接入 AI agent

把 `skill/` 复制进你 agent 的技能目录，或直接让 agent 读 `skill/SKILL.md`。skill 教给 agent 的是
*检查 → 观察 → 用数据确认 → 行动 → 验证* 这个循环：

1. **检查** —— `health` 确认桥还活着；
2. **观察** —— 截图，交给有视觉能力的模型读；
3. **用数据确认** —— 拉 `/state`，让坐标、血量、界面内容来自数据而不是像素；
4. **行动** —— 一次只做一个小动作；
5. **验证** —— 再截一张图，确认效果真的生效了。

skill 原文提到 DeepSeek Harness，但内容并不绑定任何 harness：任何能执行 shell 命令、能看图的 agent
都能用。

## 端点一览

逐字段的详细说明见 [`docs/API.md`](docs/API.md)。

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/health` | 存活检查、窗口焦点、当前界面 |
| `GET` | `/state` | 结构化游戏状态，含背包与已开界面内容 |
| `GET` | `/screenshot` | 帧缓冲 PNG（含 HUD 与界面） |
| `POST` | `/look` | 设置绝对或相对 yaw/pitch |
| `POST` | `/move` | 按下/松开移动按键绑定 |
| `POST` | `/key` | 原始按键 按下/松开/点击 |
| `POST` | `/char` | 向已打开的输入框打字 |
| `POST` | `/mouse` | 点击 / 按下 / 松开鼠标键 |
| `POST` | `/cursor` | 移动指针（界面像素坐标） |
| `POST` | `/clickAt` | 移动并点击，原子完成 —— 菜单里优先用这个 |
| `POST` | `/virtual` | 开启/关闭虚拟指针 |
| `POST` | `/scroll` | 滚轮 |
| `POST` | `/chat` | 发聊天，或以 `/命令` 执行指令 |
| `POST` | `/hotbar` | 切换快捷栏 0–8 |
| `POST` | `/gui` | 打开背包/聊天，或关闭界面 |
| `POST` | `/focus` | 把游戏窗口切到前台 |
| `POST` | `/pause` | 切换"失焦暂停" |

响应格式统一：`{"ok":true,"message":...,"data":{...}}` 或 `{"ok":false,"error":"..."}`。
**动作类端点的失败写在 JSON 体内**（而不是返回 HTTP 500），所以务必检查 `ok` 字段。

## 虚拟指针是怎么工作的

Minecraft 内部只保存一份指针位置。往里注入坐标并不会移动系统光标，所以真人一动鼠标，注入的位置就被
覆盖 —— agent 和用户抢同一个值；而且历史上注入鼠标还要求抢占窗口焦点。

开启虚拟指针（`POST /virtual {"enabled":true}`）后，一个 mixin 会把所有真实光标事件改写成保存的虚拟
坐标。默认只在界面打开时生效（`guiOnly`），世界内的视角旋转完全不受影响 —— 于是 agent 可以操作箱子
界面，而你继续用自己的鼠标。细节与实机验证记录见
[`docs/VIRTUAL-POINTER.md`](docs/VIRTUAL-POINTER.md)。

## 行为与安全说明

- **仅回环。** 服务只绑 `127.0.0.1`，局域网访问不到。若不想让本机其他进程操纵你的游戏，请设置 token。
- **会自动关闭"失焦暂停"**，方便你在别的窗口干活时游戏继续跑。
  `POST /pause {"enabled":true}` 可固定该选项，之后桥不再改动它。
- **注入键盘鼠标需要窗口焦点** —— 除非虚拟指针已开启。若某次调用提示窗口未聚焦，先执行 `focus`。
- **除非开启虚拟指针，否则鼠标控制权始终归真人**，别和人抢鼠标。
- **运行脚本时不要最小化窗口** —— 最小化会停止渲染，截图会失败。
- agent 做的任何事，都是以你的玩家身份做的。请在自己的世界/服务器使用，并遵守相应规则。

## 参与贡献

欢迎提 Issue 和 PR。代码刻意保持精简 —— 一个端点对应 `Actions.java` 里一个方法，路由在
`PilotServer.java`。新增端点时，请同时在 `docs/API.md` 补文档；如果它需要一个顺手的命令行封装，
也请在 `skill/scripts/mc.ps1` 中补上。

## 许可证

[GPL-3.0](LICENSE)。

本程序是自由软件：你可以依据自由软件基金会发布的 GNU 通用公共许可证条款（第 3 版或你选择的任何更新
版本）重新分发和/或修改它。

请注意 GPL 具有 copyleft 性质：你分发的任何修改版本也必须以 GPL 发布并提供源码。
