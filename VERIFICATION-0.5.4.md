# 0.5.4 验证记录（2026-09-16）

状态：电脑端回归测试、构建、lint 通过。手机本轮未连接，未安装 0.5.4；真实携程开屏广告仍未验收。前一轮已安装版本为 0.5.3。

## 可重复缺陷与修复

新增 CtripGestureTest 直接运行生产 AdSkipService 的扫描、候选排队、刷新校验及手势提交代码。Android 运行环境为 Robolectric/API 33，节点和系统 dispatchGesture 端点由 Mockito 模拟。

初次 11 项测试中，10 项通过，`skipBeyondTraversalBudgetMustStillBeFound` 失败：当跳过广告节点位于 350 节点遍历预算之后时，旧扫描无法发现它，手势提交从未调用。该用例是构造的复杂界面，不能证明真实携程当时恰好遇到相同布局。

修正为先调用 Android 的文字查询，取明确“跳过广告”候选，再执行原有的包名、完整文案、可见、可用、位置、窗口校验。最多处理 32 个查询结果；原有通用遍历继续保留。查询不可用时回退至遍历。没有添加固定坐标或任意广告区域触摸。

修复后原 11 项通过；补充文字查询误匹配、跨包结果、排队后文案变化及查询不可用场景，总共 15 项通过。测试还覆盖触摸型不可点击节点、刷新失败不消耗冷却、窗口变化、实时位置变化、不安全位置、功能关闭、系统拒绝、重复排队与手势取消。

这些测试证明代码在模拟的系统反馈下行为符合预期；不证明 Android 真实分发触摸成功，也不证明携程收到触摸或广告消失。

## 本机执行

本机 Windows/JDK 17 的 Gradle 测试工作进程在中文路径下发生 ClassNotFoundException。使用指向同一工作目录的 ASCII junction `E:\tools\quietstart-test` 后可运行，无代码复制。

```powershell
$jdkRoot = Get-ChildItem E:\tools\quietstart-test\.tooling\jdk -Directory | Select-Object -First 1
$env:JAVA_HOME = $jdkRoot.FullName
E:\tools\quietstart-test\gradlew.bat -p E:\tools\quietstart-test --no-daemon testDebugUnitTest assembleDebug lintDebug
.\build.ps1 -TestOnly
```

结果：15 项 Android 单元测试、10,122 项核心检查、SkipRulesTest、APK 构建、lint 均通过。Robolectric、Mockito、JUnit 仅作为测试依赖，不进入 APK。

产物：`dist/QuietStart-0.5.4.apk`，versionCode 15。没有对手机、携程安装包或携程数据作任何修改。
