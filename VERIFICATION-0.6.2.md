# 0.6.2 Clash 配合模式（2026-11-06）

用户同时安装 Clash Meta 和 FlClash，需要避免静启域名过滤占用 VPN 通道。本次 ADB 设备列表为空，未安装 APK，未读取或修改手机 Clash 配置。

## 变更

- 首页、设置增加“配合 Clash 使用”；开关保存 external_vpn_mode，并调用原暂停流程关闭静启 DNS VPN。保留无障碍、规则、白名单及订阅设置，不自动启用无障碍。
- 配合模式下首页主按钮进入自动关闭设置，不进入 VPN 授权；计数区域不再把静启旧计数当作当前过滤状态。
- 启动入口、通知权限返回后准备 VPN、VPN 授权返回后的启动，以及 onStartCommand 的系统重启/重载路径均检查配合模式。拒绝的服务启动返回 START_NOT_STICKY。
- 退出配合模式仍保持暂停；需用户手动开启静启过滤。不检测或自动启动 Clash，不自动切回 VPN，不自动导入 Clash 规则。
- 提供系统 VPN 设置入口，提示关闭静启“始终开启 VPN”及“阻止不使用 VPN 的连接”。此处只能引导，未修改系统设置。
- 文档覆盖 Clash Meta / FlClash 的完整配置合并流程，保留原节点、组和 DNS。当前只有合并工具与规则片段，没有用户的真实配置，规则合并和手机启用仍未完成。

## 验证

- 经现有 ASCII junction 执行 testDebugUnitTest、assembleDebug、lintDebug，全部通过。
- Android 桌面测试 43 项：原广告手势 31 项、无障碍状态 6 项、配合模式 6 项。新增覆盖设置保留、不擅自开启无障碍、直接启动拒绝、系统重启与重载拒绝、退出仍暂停、延迟权限回调拒绝。
- 核心测试 10141 项及 SkipRulesTest 通过。
- clash-integration/check_merge.py 通过：原配置保留、规则优先级、重复合并、重复键拒绝、实际域名；本地规则快照 105725 条。该检查不是 Clash 内核运行或手机过滤效果验证。
- git diff --check 通过。
- 未验证真实 VPN 释放、两种客户端导入、应用界面外观及无障碍同时运行；未解决之前反馈的清理后台/重启后无障碍掉线。

## 产物

dist/QuietStart-0.6.2.apk，versionCode 22，沿用本机测试签名，包含 0.6.1 修改。未发布或安装。

SHA256：FFC2D9133837D074A55E9E7A4B36E757F1EC1750D5C053AD16AFB09047D2F2BB。

构建及 Android 测试日志：.tooling/verify-0.6.2.log。

系统约束依据：https://developer.android.com/develop/connectivity/vpn （同一用户/配置文件只允许一个活动 VPN 服务）。
