# aipilot 虚拟指针（virtual pointer）

## 背景

原实现里 `/cursor` 调用的是 MC 内部的 `Mouse.onCursorPos()`，**并不会移动系统光标**；
但 Minecraft 只有一份内部指针状态，所以当人真的移动鼠标时，真实 GLFW 回调会覆盖注入的位置，
两者互相抢同一个值；同时 `/mouse` 还硬性要求窗口聚焦，导致会话必须抢用户的前台窗口。

## 改动

| 文件 | 变更 |
|---|---|
| `dev/aipilot/VirtualPointer.java` | 新增：保存虚拟坐标、开关与 `guiOnly` 开关 |
| `dev/aipilot/mixin/MouseVirtualMixin.java` | 新增：用 `@ModifyVariable` 把真实的 `onCursorPos` 坐标改写成虚拟坐标 |
| `aipilot.mixins.json` | 注册 `MouseVirtualMixin` |
| `Actions.java` | `/cursor` 同时记录虚拟坐标；`/mouse` 只在「未聚焦且未开启虚拟指针」时拒绝；新增 `/clickAt`、`/virtual` |
| `PilotServer.java` | 注册 `POST /clickAt`、`POST /virtual` |

## 新接口

```text
POST /virtual  {"enabled":true,"guiOnly":true,"x":..,"y":..}
   开启/关闭虚拟指针；guiOnly=true（默认）时仅在界面打开时生效，
   世界里移动鼠标的视角旋转完全不受影响。

POST /clickAt  {"x":426,"y":285,"button":"left"}
   一次调用内完成「设置位置 + 点击」，期间没有任何被抢占的窗口。

POST /cursor  {"x":426,"y":285}
   行为不变；开启虚拟指针后返回值会带 "(virtual)"。
```

## 实机验证（1.20.6，2026-09-12）

1. `POST /virtual {"enabled":true}` → `enabled=true`
2. `POST /cursor {426,285}` → 指向「多人游戏」按钮
3. 用 `SetCursorPos(100,100)` 模拟用户真实移动鼠标（真实 GLFW 事件）
4. **不重新注入位置**直接 `POST /mouse {"button":"left","action":"click"}`
5. 结果 `screen=class_500`（多人游戏界面）——证明虚拟位置没有被真实移动冲掉

1.21.1 客户端同样加载新 mod 并验证 `/virtual`、`/cursor` 正常。

## 使用注意

- 开启虚拟指针期间，**用户自己在 MC 窗口内的点击也会使用虚拟坐标**，所以脚本运行时应只用其他窗口；
- 窗口可以切到后台，但**不要最小化**（最小化会停止渲染，截图会失败）；
- `/look` 直接改 yaw/pitch，与指针无关，不受影响；
- 旧的 jar 备份在各 mods 目录下的 `*.bak-virtual`。
