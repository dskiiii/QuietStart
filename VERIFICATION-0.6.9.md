# 0.6.9 · 界面精简

本地版本，未发布 GitHub。版本号 0.6.9 / 29。

安装包：`dist/QuietStart-0.6.9.apk`，1,041,437 字节。SHA256：`2E28942DFF53BE178545C426C807A0B1855089EF9223B1101DAB06BB8F7C77A2`。

## 用户可见变化

- 首页保留过滤按钮、自动跳过开关、简短运行状态和设置入口。删除重复说明与“收起”按钮，系统返回仍可退出页面。
- 设置保留广告弹窗开关，以及过滤范围、后台与权限、高级、关于四个入口。
- 规则编辑、排除应用、兼容设置、诊断移入高级。移除日常页面中的事件数、扫描数、包名、各应用识别原理及调试记录；需要排查时可在诊断中查看。
- 设置子页不再重复展示全局运行状态和请求计数。启用全部应用时隐藏单个目标列表。
- 后台页面与系统无障碍说明缩短；只在缺少权限或连接异常时显示首页处理入口。既有授权逻辑不变，不自动申请电池豁免。
- 修正多层返回路径，兼容升级前保存的自动关闭页面。保留已有规则、白名单、排除名单和开关值。
- 无新增依赖或后台任务；私人 DNS 状态检测仅在诊断页面执行。

## 验证

- 134 项 Android 单元测试，0 失败、0 错误、0 跳过。新增用例覆盖多层导航、保存页面恢复、旧页面迁移、浏览设置不改变用户选项且不主动申请权限。
- CoreTest 10141 检查及 SkipRulesTest 通过；assembleDebug、lintDebug 通过。
- Xiaomi 17 Pro Max / Android 16 覆盖安装成功，确认 0.6.9 / 29。检查首页、设置、高级、规则、后台页面排版及返回路径。
- 过滤已恢复，无障碍服务 Bound，首页显示自动跳过已开启。此次为界面与连接验证，不增加真实广告成功关闭的验收结论。
- 手机实测首页自动跳过关闭后显示“已关闭”，再次打开直接恢复“已开启”，未要求重新授权；VPN 过滤保持开启。
- 手机截图和构建日志保存在被 gitignore 排除的 `.tooling/ui-069/` 与 `.tooling/ui-069-final-build.log`。

## 连接调查

本轮开始时同一手机同时列出 USB `offline` 和 mDNS 无线 `device`。指定无线 transport 可读取手机信息，指定 USB transport 返回 `device offline`。电脑 ADB 进程与手机局域网地址的 TLS 调试端口有 Established TCP 连接，确认安装和截图实际走 Wi-Fi。

手机 USB 管理器报告 connected / configured / CONFIGURED，USB 调试和无线调试设置均为 1，adbd 为 running。Windows USB 设备使用 Microsoft WinUSB，ProblemCode 0；这只能说明设备被枚举和驱动加载，不能证明 ADB 握手成功。

仅重连 offline 设备后，USB 从 ADB 列表消失，无线仍正常。随后经无线执行一次系统 `svc usb resetUsbGadget`，再次指定 USB 查询仍返回 no devices found。没有重启手机、替换驱动、撤销授权或反复重启 ADB。无线连接没有因此中断。

结论：开启无线调试建立了另一条可用链路，未修复 USB。有线调试本身不要求同时开启无线。当前仍未确定 USB 故障归属，不能断言线坏、电脑坏或手机坏；也不能报告有线已修好。静启日常运行不依赖 ADB 或无线调试。

参考：[Android 官方 ADB 文档](https://developer.android.com/tools/adb)、[AOSP 无线 ADB 架构](https://android.googlesource.com/platform/packages/modules/adb/+/HEAD/docs/dev/adb_wifi.md)。
