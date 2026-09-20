# 网易云广告加载排查（2026-09-16）

状态：基线广告已复现，广告模块只读分析完成一部分；DNS 对照测试未完成，未新增拦截规则、未发布或安装新版。

## 基线

手机包名 `com.netease.cloudmusic`，启动入口 `.activity.IconChangeDefaultAlias`。
静启未运行 VPN，无障碍没有干预；截图 `.tooling/netease-analysis/baseline-2.png` 显示咖啡视频广告及“跳过 5”，`baseline-4.png` 显示“跳过 2”。这是真实广告展示证据，不是成功去广告证据。

主空间和分身空间的已安装包列表均未找到 `com.baidu.netdisk`；主空间存在 `com.baidu.BaiduMap`。已向用户确认所指百度应用，尚未收到答复，不能将地图自动当作网盘测试。

## 广告模块证据

复制安装的 APK 至本地忽略目录 `.tooling/netease-analysis/netease.apk`。使用已有 jadx 工具，仅输出 `classes18.dex` 中 `com.netease.cloudmusic.module.ad.*` 的类，未修改网易云 APK、应用数据或缓存。反编译因缺少其他 dex 类型存在警告，以下仅记录明确可读的路径和控制流。

`ad-source/sources/com/netease/cloudmusic/module/ad/b.java` 的 Kotlin 元数据指向 `AdPreLoadHelper`：

- B 方法选择 `interface3.music.163.com` 或 `ipv4.music.163.com`，拼接 `/eapi/` 请求路径及 `/api/` 签名路径。
- K 方法请求 `ad/loading/current`；A 方法请求 `ad/prefetch/select`。
- I/J 方法从 AdIPCacheUtil / AdInterface3IPCacheUtil 获取地址列表，缓存为空时使用后备解析或地址获取逻辑。
- x 方法在配置条件成立时把地址字节传给 `InetAddress.getByAddress("interface3.music.163.com", bytes)`，构造地址列表；是否在本次广告中触发仍需运行时验证。

这给出了 DNS 拦截可能不起作用的具体实现路径，但不能仅凭静态代码认定本次广告必然使用 IP 直连，也不能认定本次视频来自缓存。

现有 `anti-ad-full.txt` 已包含 `admusicpic.music.126.net`、`iadmusicmat.music.126.net`、`iadmusicmatvideo.music.126.net`、`iadmat.nosdn.127.net` 等素材域名。未盲目追加封禁 `music.163.com` 或接口服务器 IP。

## 尚未完成的对照

计划只让网易云经过静启，并关闭自动点击，记录 DNS 查询后比较开屏行为。已备份静启设置至忽略目录 `settings-before.xml`，临时选择网易云单应用及诊断记录。但手机进入锁屏，系统 `deviceLocked=1`，未完成开启过滤和重启广告的对照。

结束前已停止静启并完整恢复该设置备份；临时单应用范围、diagnostics=true 和 skip_ads=false 均未遗留。原设置备份的 user_paused=false，但实际 VPN 已停止。没有更改手机锁屏、网络共享或安全设置。

下一项验证是单应用 DNS 对照及正常功能检查；若证实广告绕过 DNS，应评估连接层/请求级处理，而不能继续以新增跳过按钮或无证据域名封禁代替根源排查。
