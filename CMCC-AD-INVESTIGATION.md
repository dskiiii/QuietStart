# 中国移动开屏广告排查（2026-09-16）

## 2026-09-20 深入拆包结果

已用 aapt 解析 APK 的资源表、实际开屏布局和 Manifest；没有修改或重新签名第三方 APK。

- `res/layout/app_startpage.xml` 可正常解析，包含图片、视频控件、明确“广告”TextView、右上角控件以及底部广告跳转区域。输出保存在 `.tooling/cmcc-analysis/start-layout.txt`，资源表在 `resources.txt`。
- 加固组件确认为 `com.secneo.apkwrapper.AP`。`libdatajar.so` 字符串中找到完整 `/DN/init/startInit`，偏移 `0x4083371`；也找到 `/startInit`、`startInitInfo`、StartPageReq/Res、StartPagePresenter、StartPageHelper 等符号。证据见 `endpoint-symbols.txt` 与 `endpoint-context.txt`。这仍不是恢复出的 Java 调用链。
- [公开重写规则](https://github.com/ceadmond/Rewrite/blob/main/chongxie.conf) 针对 `client.app.coc.10086.cn/biz-orange/DN/init/startInit` 返回空响应。当前 APK 包含同一路径后缀，说明值得验证；公开规则不构成本 Android 版本上的有效性证据，不能直接宣布它只影响广告。
- 实机 DNS 记录中已有 `client.app.coc.10086.cn` 放行。现有静启只处理 DNS，不能匹配 HTTPS 请求路径。将整个主机封禁不能等同于只处理上述接口。
- Manifest 指向 `res/xml/network_security_config.xml`，其中只有 `base-config cleartextTrafficPermitted=true`，未声明信任用户证书。targetSdk=30，按 [Android 默认信任规则](https://developer.android.com/privacy-and-security/security-config) 不能据此假定安装用户 CA 就能解密请求；自定义网络栈的行为尚未查明。
- `run-as` 返回目标包不可调试；shell PATH 未找到 su，ro.debuggable=0。普通 ADB 无法直接读取该进程私有数据或据此完成运行时脱壳。上述检查不等于证明设备不存在任何提权方式，也未尝试获取 root。

结论：已经得到可验证的接口候选和实际广告布局，但仍未解开加固代码，未抓到目标接口的运行时请求/响应。当前限制同时涉及静启只有 DNS 能力和缺少目标 HTTPS/进程内部观察条件。未安装证书、修改系统信任、解锁引导程序、覆盖中国移动安装或清除用户数据。未交付声称已去广告的新版本。

状态更新（2026-09-20）：DNS 过滤仍未阻止广告展示，尚未确定加载请求。0.5.6 曾有两次提前跳过，但最新回归在两项服务都运行时再次失败，不能称为已修复。详见 [验证记录](VERIFICATION-0.5.6.md)。下文保留早期只读排查经过。

## 实机证据

- 包名 `com.greenpoint.android.mc10086.activity`，版本 12.5.4（125401）。
- 启动入口 `com.mc10086.cmcc.base.StartPageActivity`。
- 静启 VPN 运行时重启应用，`.tooling/cmcc-analysis/current-2.png` 显示全屏推广，左上有“广告”，右上为“2 跳过”；`current-5.png` 已进入首页。此次未观察到自动点击。
- 无障碍服务未绑定，不能用这次测试评价其点击执行结果。另外现有开屏文本规则要求“跳过广告”，无法直接匹配此次“2 跳过”。这与阻止广告加载是两个问题。
- 单凭展示不能判断素材来自缓存、遗漏的域名还是绕过系统 DNS 的请求。

## 只读 APK 分析

本地 APK：`.tooling/cmcc-analysis/cmcc.apk`，137498746 字节。安装包存在加固，普通 jadx 无法找到 StartPageActivity，不能声称已反编译启动页实现。

从 `lib/arm64-v8a/libdatajar.so` 提取可读字符串，保存为同目录下的 `ad-strings.txt`、`start-strings.txt`、`embedded-urls.txt`。存在 StartPageHelper、StartPageView、onDownloadSuccess/onDownloadFailed、startAdImg、startAdLabel、skipStartPageAd 等名称；只能说明相关符号存在，尚不能恢复调用关系。

发现 `biz-orange/DH/indexInitPage` 以及 `client.app.coc.10086.cn`、`clientaccess.10086.cn`、`img.app.coc.10086.cn`。没有证据证明前者专用于广告，也没有证据证明这些主机可整体封禁。素材主机还含普通图标地址，未加入黑名单。`/SA/advertisingClickNew/printLog` 是点击上报命名线索，不能当作素材加载入口。

未修改第三方 APK、清除其数据或删除其缓存。可读外部 cache 目录为空，这不排除其他位置的缓存。

## 待验证与设备状态

继续测试前手机已锁定：`deviceLocked=1, strongAuthRequired=0x4`。已请用户解锁，未绕过锁屏。静启 VPN 保持运行；此次未改动其偏好设置。

下一步需要在解锁状态下对照广告展示与 DNS 记录，并验证是否存在广告专用设置。个性化推荐关闭不能直接等同于关闭全部广告。当前 VPN 仅接管虚拟 DNS 地址，无法凭现有实现识别 HTTPS URL 路径；若广告与业务共用主机，增加域名规则可能无法安全解决。
