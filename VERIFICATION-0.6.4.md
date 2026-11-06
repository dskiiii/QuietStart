# 0.6.4 开屏跳过修订（2026-11-06）

设备：Xiaomi 17 Pro Max（2509FPN0BC），Android 16。中国移动 11.9.2（119200），京东 15.8.30（102368）。本轮针对自动点击，不增加 DNS 封禁规则。

## 中国移动

0.6.3 无障碍仍已连接、skip_ads=true，但多轮截图显示广告继续倒计时，没有点击日志。当前版本的广告标记位于图片中，读取到的控件列表没有独立“广告”文字。旧规则必须读取该文字才允许兼容触摸，导致漏过。

实机读取到原生 TextView `com.greenpoint.android.mc10086.activity:id/video_time_skip`，文字为“3 跳过”等，clickable/visible/enabled 均为 true。其父控件为 `ll_top_right`，上级为 `video_layout`；采样边界为 Rect(947,115 - 1152,205)。新规则核验这些 ID、同包层级、倒计时文字和右上角小范围，点击前重读节点、核对活动窗口及边界，使用 ACTION_CLICK。

候选构建两次真实点击记录为 22:49:59.855、22:50:32.395。两轮均拍到“5 跳过”的广告画面，随后进入首页。证据为 `cmcc-native1-2.png` → `cmcc-native1-3.png`、`cmcc-native2-2.png` → `cmcc-native2-3.png` 及同名前缀日志、采样时序。未手动点击广告。这是有限轮次的提前跳过证据，不代表阻止广告加载，也不代表其他版本全部有效。

最终 0.6.4 再次记录 22:55:02.569 原生点击；`cmcc-final64-2.png` 显示“3 跳过”，后一张已为首页。由于这次截图传输间隔较长，单独这一轮不计作额外的精确提前退出证明。最终版本的中国移动点击逻辑与前两轮候选构建相同。

## 京东

用户确认是打开应用时右上角带“跳过”的全屏广告。多次冷启动、后台返回和 launcher intent 启动均未捕获同样的广告；用户安装新版后自己尝试，也反馈本次没有出现广告。**京东实机跳过仍待验证，未投放不计为成功。**

新增仅限京东进入 MainFrameActivity 后 10 秒内的规则，接受完整“跳过”、倒计时或明确跳过广告标签，限制右上角小尺寸按钮；离开主页面、超时、排除应用或标签变化后不点击。文本索引查不到时，进行最多 350 个节点的遍历，优先访问后添加的覆盖层。每次点击前复核活动窗口、标签和边界，不使用固定坐标。原有通用明确广告关闭规则不受该时间范围影响。

参考了 [GKD 订阅中的京东适配观察](https://raw.githubusercontent.com/AIsouler/GKD_subscription/main/src/apps/com.jingdong.app.mall.ts)：部分开屏跳过节点不支持快速文字查询，搜索页需要排除。本项目独立实现更窄的主页面、时间和完整标签条件，没有导入该订阅的权限、支付或其他页面操作规则。

## 验证与留存

- 74 项桌面 Android 测试通过，0 失败、0 错误；其中新增 18 项覆盖中国移动原生布局，以及京东文字索引漏查、超时、搜索/支付页面、窗口变化、标签变化和排除应用等。
- 核心 10141 项检查、SkipRulesTest 通过；assembleDebug、lintDebug 通过（lint 0 错误、3 项警告）；git diff --check 通过。
- 最终 APK：`dist/QuietStart-0.6.4.apk`，versionCode 24，SHA256 `6F059A2A69AEE5C6CEB669523C5D23DA40A6EB563C4A26FD5DEF01BDFEB78DAE`。覆盖安装，保留静启和第三方应用数据，未修改订阅、账号或购买设置。
- 原始证据在本机忽略目录 `.tooling/jd-cmcc-20261106/`。诊断采用独立临时测试包和 UiAutomation 的 [FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES](https://developer.android.com/reference/android/app/UiAutomation#FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)，不通过默认 uiautomator dump 停用静启。成功点击验收阶段没有运行诊断工具。
- 早期 app_process 诊断尝试因未初始化主 Looper 崩溃；属于临时探针进程，不是静启崩溃。覆盖安装时系统短暂标记静启服务已断开，随后自动重新绑定，未重新要求无障碍授权。
- 结束时静启无障碍处于 Enabled/Bound，Binding/Crashed 为空；过滤运行中，系统 VPN 网络 254 属于静启。临时诊断包 `cn.quietstart.probe` 已卸载，临时手机端探针 jar 已移除；已切回 USB 调试并断开网络 ADB，实测 TCP 5555 端口不可连接。
