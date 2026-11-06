# 0.6.3 自动交接 VPN（2026-11-06）

用户要求直接修改：只操作 Clash 开关，静启自动让出并在其退出后恢复，不再手动切换配合模式。本文记录最初本地实现及桌面验证；后续同日已在 Android 16 手机安装并完成指定切换验收，见 [手机实测记录](VERIFICATION-0.6.3-PHONE.md)。尚未公开发布。

## 最终行为

- 首页开启一次静启过滤后，使用系统 VPN 网络回调等待交接。若开启时其他 VPN 已存在，先等待，不调用会改变 VPN 所有权的 prepare()。
- 观察到其他 VPN 时释放静启 TUN、DNS 读取线程和查询池；保留前台通知与 VPN 网络观察。无障碍服务及其设置独立保留。
- 观察到外部 VPN 退出后等待 2 秒，再检查回调缓存与实时网络快照。其他 VPN 出现或尚未取得完整网络信息时取消恢复；仅在条件满足时尝试一次恢复。
- 沿用此前系统同意状态。prepare() 返回 null 时直接尝试建立 TUN；返回确认 Intent 时更新通知/首页，等待用户处理，不后台打开授权页面、不按定时器重复申请。
- 仅收到自身 VPN 撤销或接口关闭、未观察到其他 VPN 时，保持等待，不凭撤销事件立刻抢回通道。设置页保留手动重试入口。
- 手动暂停注销观察、取消待恢复回调、保存暂停状态。暂停代次同时作废尚未结束的界面授权请求，避免迟到的权限回调重新开启。
- 系统重建等待中的服务时，读取此前交接记录；这不保证被强行停止或系统清理后仍可运行。
- 移除 0.6.2 手动配合开关；旧暂停状态保留，重新开启时清除旧模式标记。未自动更改无障碍、广告规则、白名单或 Clash 配置。

## 实现与边界

VpnMonitor 注册仅含 VPN 传输类型的 NetworkRequest，移除默认排除 VPN 的能力条件。读取 VPN 所有者 UID 判断自身；系统隐藏 UID 时，以静启的虚拟地址和 DNS 地址组合兜底识别。未取得完整元数据时阻止恢复。

等待时不占 VPN；同一个用户启动的服务以可见通知保留事件接续。Android 14+ 声明 specialUse 前台服务及具体用途，避免把未持有 VPN 的等待状态当作活跃 VPN。未增加后台轮询、定时任务、WakeLock 或依赖无障碍的保活机制。

需要关闭静启的“始终开启 VPN”及“阻止不使用 VPN 的连接”。不要在 Clash 应用访问控制中排除静启，否则系统可能隐藏外部 VPN 的状态。只能检测系统提供的网络退出事件，不能区分用户关闭 Clash、长时间重连或崩溃；网络退出也可能触发恢复。两个应用切换之间不存在公开的原子交接接口，真实设备时序仍需验收。

Clash 占用 VPN 期间静启 DNS 过滤暂停，广告过滤效果取决于 Clash 自己的规则；无需为了自动切换先合并配置。本次未修复无障碍重启掉线，也未增强广告识别规则。

## 验证

- 经现有 E:/tools/quietstart-test ASCII junction 执行 testDebugUnitTest、assembleDebug、lintDebug，最终全部通过。
- 56 项 Android 桌面测试：广告节点/手势 31 项，无障碍状态 6 项，交接服务 15 项，网络归属 4 项。
- 新增覆盖已有外部 VPN 时不 prepare、断开延迟、同意状态复用时尝试建立 TUN、两个客户端切换、恢复前新网络出现、手动暂停、旧回调、onRevoke 保留观察、无外部 VPN 证据不恢复、等待中规则重载、未知网络元数据、进程重建、销毁取消恢复、Android 14 前台等待、迟到授权请求作废。
- 授权接口和系统 TUN 创建端点采用模拟对象；成功复用授权的测试验证调用建立接口，不代表真实 TUN、DNS 服务或 Clash 已实机成功交接。Android 14 前台服务测试亦不能替代厂商 ROM 验证。
- 初轮测试夹具误用了 SDK 隐藏构造器，以及复制 Service 的 Mockito spy（字段闭包仍指向原实例），已改为真实 Robolectric Service 和模拟系统网络服务。最终没有测试失败。
- build.ps1 -TestOnly：10141 项核心检查及 SkipRulesTest 通过。git diff --check 通过。
- 最初桌面阶段未实测手机；后续手机切换结果见上述补充记录。真实广告、长期耗电及系统清理恢复仍未验收。

## 产物

dist/QuietStart-0.6.3.apk，versionCode 23，沿用本机测试签名，包含此前 0.6.1 状态提示修复。

SHA256：D069D9E1861D331AB3E5563F276313A059E847C409741994DFC52346BAACD7C3。

最终日志：.tooling/verify-0.6.3.log。

接口依据：

- https://developer.android.com/reference/android/net/VpnService#prepare(android.content.Context)
- https://developer.android.com/reference/android/net/NetworkRequest.Builder
- https://developer.android.com/reference/android/net/NetworkCapabilities#getOwnerUid()
- https://developer.android.com/develop/background-work/services/fgs/service-types#special-use
