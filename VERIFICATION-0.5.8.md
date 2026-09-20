# 0.5.8 百度地图开屏兼容（2026-09-20）

用户确认问题为打开时全屏广告，不是首页推广。

## 已做

- 手机百度地图：我的 → 设置 → 通用设置 → 允许展现开屏「摇一摇」广告，从开改为关。截图 general2.png 为修改前，general3.png 为关闭后。页面明确说明不影响其他广告展现，不能作为开屏广告总开关。
- 只读提取原 APK，resources.txt 确认 com.baidu.BaiduMap:id/ms_skipView 存在。
- 参考 https://github.com/AIsouler/GKD_subscription/blob/main/src/apps/com.baidu.BaiduMap.ts 中开屏控件标识（本地 gkd-reference.ts 保存），独立实现严格控件点击：仅该包和该 ID、小尺寸右上角、可见可点击、活动窗口一致、刷新后 ID/包名/位置不变。不扩展首页推广处理，不增加盲点坐标。
- 版本 0.5.8（19）已装入用户手机，VPN 和无障碍服务均运行。

## 验证与限制

testDebugUnitTest：31 项通过，0 失败；assembleDebug 和 lintDebug 通过。新增用例覆盖正常点击、ID 变化、窗口变化、不可见与大区域拒绝。

本轮新旧版本启动截图均未捕获全屏广告，新版也没有跳过点击日志，因此尚未验证真实广告成功关闭。不能以本次无广告宣称问题已解决。此规则只补充一种已知广告控件，其他 SDK 的开屏可能仍漏掉。

本地截图和安装包分析材料在 .tooling/baidumap-analysis/，没有修改百度地图原 APK、清除应用数据或操作首页推广。
