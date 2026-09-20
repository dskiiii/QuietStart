# 中国移动隔离验证（2026-09-20）

结论：已完成独立环境准备并实际测试原 APK 启动；阻塞在加固库与 x86_64 模拟环境的兼容性，尚未获取广告接口响应，未验证任何接口拦截规则。

## 环境和范围

- 使用本机现有 MuMu，通过其 CLI 新建实例 1，命名 `QuietStart-CMCC-Lab`；原实例 0 未修改。
- 仅新实例启用 root_permission，以便运行时观察。设备 ABI 为 x86_64，ADB 地址 `127.0.0.1:16416`。所有实验 ADB 命令显式指定该地址，未操作用户实体手机。
- 安装从用户手机读取的原版中国移动 12.5.4 APK，未重签名、未修改 APK、未登录账号。
- 工具装在 `.tooling/cmcc-analysis/lab-venv`：Frida 17.18.0、frida-tools 14.10.4、pyelftools 0.33。Frida server 取自官方 GitHub 同版本发布，只在模拟器回环地址监听，主机临时转发端口 27043。

## 已完成的实验

1. 原包可安装，但启动即退出。错误为 `UnsatisfiedLinkError: No implementation found for boolean com.secneo.apkwrapper.H.is(int)`，未进入正常业务界面。
2. JADX 成功解析未加密的壳类 `H`、`AW`、`AP`。`H.a(ApplicationInfo)` 根据 `H.c()` 选择 `DexHelper-x86` 或 `DexHelper`；APK 只有 arm64-v8a，未包含对应 x86 库。输出位于 `.tooling/cmcc-analysis/wrapper/`。这不代表已解开业务 DEX。
3. 第一份进程观察脚本尝试改变库选择函数，初始化时机过晚，仍出现相同 JNI 缺失错误。
4. 第二份脚本在 `H.a(ApplicationInfo)` 中明确通过该应用类加载器加载原包 `DexHelper`，进入原生执行后发生 `SIGSEGV SI_KERNEL`。尚不能确定是 ARM 转译不兼容还是其他加固行为，不能擅自归因于反调试。
5. ELF 检查：DexHelper 大部分节名/符号不可用，datajar 未提供普通 DEX 头或动态符号。这一版本不能直接套用已发表的其他 SecNeo 变体脱壳脚本。

脚本、日志和截图保存于 `.tooling/cmcc-analysis/lab/`，包括 `bootstrap.js`、`bootstrap2.js`、对应日志、`crash-log.txt`、`initial.png`。本次结束关闭测试实例并移除主机临时转发；保留实例和文件供后续分析。

## 未完成及后续条件

尚未抓到 `/biz-orange/DN/init/startInit` 的请求或响应，不能判断返回空内容是否损坏启动配置。尚未实现广告响应字段过滤，未做登录或首页回归，没有给手机安装新静启版本。

下一条有依据的路径是在可调试的原生 ARM64 Android 测试环境运行相同原包，或者进一步逆向当前 DexHelper 的静态解密过程。当前 MuMu 实例不能充当该版本的有效动态验证环境。不得把测试环境问题当作“中国移动不能去广告”的证据，也不得把找到字符串当作拦截成功。

方法参考：[Quarkslab SecNeo 分析](https://blog.quarkslab.com/dji-the-art-of-obfuscation.html)。文章明确其 DxFx 示例仅针对所分析 DJI 版本，不是通用 SecNeo 解包器；本次未将该示例当作已验证工具。
