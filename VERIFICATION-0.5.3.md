# 0.5.3 携程开屏诊断（2026-09-15）

状态：构建和规则检查通过，已安装到连接手机；**未验证真实携程广告成功跳过**。

## 问题与修正

用户确认目标是启动时全屏广告，右上角倒计时“跳过广告”。0.5.2 在将候选节点排入主线程时就更新 lastClick；即使随后窗口检查或节点刷新失败，也会进入 2.5 秒冷却。0.5.3 只在 dispatchGesture 返回 true 后开始冷却，系统取消时清除该冷却。节点刷新后的新边界仍须满足同应用、同窗口、可见、可用、明确跳过文字和右上角小区域限制；无需与旧边界完全相等。触摸时长改为 ViewConfiguration.getTapTimeout()。

新增 QuietStartTap 日志及设置页最近携程触摸状态，区分候选识别、窗口变化、失效节点、不安全边界、系统拒绝、触摸完成和取消。仅保存最新状态及候选几何位置，不保存界面文字或截图。触摸完成只表示系统执行手势，不表示广告确认消失。

## 只读分析发现

手机携程 APK 被复制至本地忽略目录 `.tooling/ctrip-analysis`。未修改、重新安装携程或删除其数据。仅对启动广告相关类与广告 SDK 进行分析，未将反编译源码纳入本项目。

- AdSplashViewLayout 为 skipLayout 安装 OnTouchListener，doOnTouch 在 ACTION_UP 的 type=1 分支调用跳过回调。与此前观察到文字及小父容器 clickable=false 一致。
- HomeSplashAdBusPresenter 经 adsdk/displaySplashAd 请求广告视图，包含等待超时判断。
- 广告 SDK 管理器包含缓存素材、预加载、按日期保存展示次数和达到次数上限不展示的分支。尚未读取到手机当前实际配额，不能断言本次不展示一定由配额造成。
- 研究 [GKD 点击实现](https://github.com/gkd-kit/gkd/blob/HEAD/gkd-app/src/main/kotlin/li/gkd/app/data/GkdAction.kt)：普通节点点击失败后可使用节点中心手势及系统 tapTimeout。未复制 GPL 源码。
- 查阅 [AIsouler 携程规则](https://github.com/AIsouler/GKD_subscription/blob/main/src/apps/ctrip.android.view.ts)：其多种首页弹窗使用特定结构选择器，不能将这些规则视为本次开屏广告已修复的证据。

## 验证

- build.ps1：10,122 项核心检查、SkipRulesTest、assembleDebug、lintDebug 通过。
- APK 0.5.3 / versionCode 14 安装成功；无障碍服务重新连接，具备读取控件和手势能力。
- 开启过滤和暂未启动过滤两种状态均尝试重开携程；连续截图观察到首页，未捕获本次目标开屏广告。QuietStartTap 未有对应候选/触摸记录。
- 不使用 uiautomator dump 进行广告观察，因为它会干扰其他无障碍服务。
- 未将此前测试页面点击计数当作携程成功次数；未做真实广告手势端到端成功声明。

APK SHA256：`FF45D018ADE8D1241E0681FD30F56DD00D4E6DBE3796BBDEA70467F8597504E5`。
