# Clash Meta for Android 去广告整合

本目录提供配置合并工具，需使用自己的 Clash 配置生成结果，不包含节点配置。

需要手机正在使用的完整 YAML 导出文件，才能保留节点、策略组、DNS 和分流规则。订阅链接不是必需的。配置可能包含节点凭据，合并工具只在本机解析，不上传、不打印正文、不覆盖原文件。最终文件同样应当私下保存。

## 两种合并方式

- 在线规则：新增 anti-AD HTTP domain rule-provider，刷新周期 86400 秒。在 rules 最前面引用 REJECT，后接此前精选补充规则，再保留原有规则顺序。首次需要能下载 GitHub 规则。该地址是公开规则库，不是用户节点订阅。
- 离线规则：把与静启相同的完整域名快照和补充规则直接转为 DOMAIN-SUFFIX/REJECT 放到 rules 前面；不需要首次下载规则，但文件较大，广告规则不会自动更新。

强制规则模式，避免全局/直连模式绕开规则。除 mode、rules 和在线模式新增 rule-providers 外，保留其他配置字段。已有白名单/特殊放行规则可能需要人工调整到广告规则之前。对重复 YAML 键或冲突的 provider 名称拒绝合并，避免静默丢失配置。

仅在本机使用（需要 Python / PyYAML）：

```text
python merge_config.py 原配置.yaml 新配置-去广告.yaml
python merge_config.py 原配置.yaml 新配置-离线去广告.yaml --offline
```

## 手机上切换

1. 保留原配置作为回退，导入合并后的文件为新配置。
2. 暂停静启，关闭静启的“始终开启 VPN”（如有）。如果此前试过私人 DNS，将其恢复到原设置。
3. Clash 选择新配置，覆写中的运行模式保持规则模式或不修改；启动 Clash 并授予 VPN 权限。
4. 访问控制要包含希望过滤的应用；被排除的应用不会走 Clash 广告规则。
5. 强行停止目标应用再打开；在 Clash 日志/连接中检查广告域名是否命中 REJECT，并测试音乐播放、地图定位等正常功能。

由文件导入的是配置快照。原节点订阅日后变动，可能需要重新合并；在线广告 rule-provider 的定期更新不能代替节点订阅更新。不能声称一次导入就永久跟随机场订阅。

此方案不需额外购买广告服务，但 Clash 原有节点费用按你已有服务处理。Clash 自身的系统 VPN 图标仍然存在。DNS 域名过滤与 Clash 连接规则拒绝不是完全相同的处理路径，实际客户端效果仍须复测。

## 依据

- https://wiki.metacubex.one/config/rule-providers/
- https://wiki.metacubex.one/config/rules/
- https://github.com/MetaCubeX/ClashMetaForAndroid/blob/main/core/src/main/java/com/github/kr328/clash/core/model/ConfigurationOverride.kt
- https://github.com/MetaCubeX/ClashMetaForAndroid/blob/main/design/src/main/java/com/github/kr328/clash/design/OverrideSettingsDesign.kt
- https://github.com/privacy-protection-tools/anti-AD

anti-AD 的 MIT 原许可随本目录提供。规则数据仍来自工作区的原始快照。
