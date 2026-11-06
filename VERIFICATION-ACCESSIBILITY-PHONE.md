# 无障碍与后台保护设置实测（2026-11-06）

设备：Xiaomi 17 Pro Max（2509FPN0BC），Android 16；静启 0.6.3。本轮调整手机设置，没有改代码或重装应用。

## 实际发现

- 开始时系统 enabled_accessibility_services 为空，静启没有已绑定的无障碍服务，包状态 stopped=true。
- 系统退出记录中有多次 LockScreenClean、SwipeUpClean 强制停止静启。最近一条为 2026-11-06 22:17:49，USER REQUESTED / FORCE STOP，说明为 stop cn.quietstart due to SwipeUpClean。这证明系统清理实际发生过，但不能单凭这些记录确定每次无障碍授权丢失的原因。
- 静启自启动原为关闭，省电策略为“智能限制后台运行（推荐）”，锁定任务列表中原未锁定。
- 系统锁屏清理内存原设为“10分钟后”，本轮保持原值。

## 已完成设置

1. 在静启应用信息中开启自启动，并核对开关状态。
2. 将静启省电策略设为“无限制”，核对选中状态。
3. 在系统内存优化设置的“锁定任务管理”中锁定静启，核对该项开关为 true；保留原有其他锁定应用。
4. 在系统已下载的无障碍应用中启用“静启 · 自动关闭广告”，等待正常警告倒计时结束后确认，没有通过写安全设置绕过授权流程。
5. 开启静启过滤，恢复既有使用状态。

## 验证与边界

- dumpsys accessibility 显示静启位于 Enabled services 和 Bound services，Binding/Crashed services 为空。应用首页显示“自动关闭广告：已连接”。
- 点击静启“收起”后，再启动首页，两次状态检查均保持授权与绑定，进程 PID 均为 27746，没有再次弹出授权。
- 最终检查无障碍仍已连接；FilterVpnService 为前台服务，系统 VPN 网络 250 属于 cn.quietstart。
- 启用无障碍后未运行 uiautomator dump，避免观察工具干扰服务。
- 测试结束已切回 USB 调试并断开临时网络 ADB，实测临时 TCP 5555 端口不可连接。
- 尚未测试重新启动手机、手动清空后台、超过 10 分钟的锁屏清理、全天稳定性及真实第三方广告识别。因此不能声称已保证永久保活或彻底解决所有授权丢失。
- 原始证据位于本机忽略目录 .tooling/accessibility-phone-20261106/：exit-before.txt、accessibility-before.txt、battery-before.xml、battery-unrestricted.xml、lock-target-after.xml、enabled-accessibility.txt、after-hide-accessibility.txt、after-reopen-accessibility.txt、final-accessibility.txt，以及相应截图。
