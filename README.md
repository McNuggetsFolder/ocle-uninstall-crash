注意：该mod全部有deepseek 4.1 flash（workbuddy）完成

# 加载绝对彼方就卸载崩溃

> Minecraft **1.20.1** / Forge **47.x** / Java **17**
> modId: `ocle_uninstall_crash` ｜ 包名: `cn.jikuai.ocleuninstall` ｜ 版本: 1.0.0

一个「反枪包」模组：只要你的 `.minecraft` 里存在 **绝对彼方**（TaCZ 枪包，`gunpack.meta.json` 中 `namespace=ocle`），
本模组就会在启动最早期把它**移入回收站**（软删除，不丢数据），然后**主动崩溃**，绝不让你进游戏。

日志与崩溃报告里会原样出现这句话：

```
请勿同时加载此mod和绝对彼方
```

---

## 一、它到底做了什么

启动流程里的插入点只有一个：**模组构造阶段**（Forge 初始化最早期，早于方块/物品注册，早于 TaCZ 读取枪包）。

| 阶段 | 行为 |
| --- | --- |
| 构造（`new OcleUninstallCrash()`） | 读 `options.txt` 确定语言 → 扫描游戏目录 → 定位绝对彼方产物 |
| 未命中 | 只挂一个 `FMLLoadCompleteEvent` 监听器，写一行 info 日志，**完全静默** |
| 命中 | 逐条**移动到 `ocle_guard_trash/<时间戳>/`** → 写 `manifest.txt` + `RESTORE.txt` → 注销自身监听器 → 写崩溃报告 → 抛 `IllegalStateException` |
| 结果 | Forge 走「Mod loading has failed」通道，启动终止，并生成官方崩溃报告 |

抛异常是刻意选的：它由 Forge 的模组加载异常通道接管，客户端必然停在崩溃界面，不会进入主菜单、更不会读档。

---

## 二、判定规则（双通道，但只认指纹）

「目录名」只用来**扩大搜索范围**，真正决定命中与否的永远是**内容指纹**。
所以：`tacz` 里有别人的枪包 → 不崩；目录被改名成 `abc` 但里面是绝对彼方 → 照样崩。

### 2.1 搜索范围

1. **标准路径**：`.minecraft/tacz`、`.minecraft/versions/*/tacz`
2. **相似目录**：深度 ≤4 遍历游戏目录，目录名满足下列任一条件即纳入检测
   - 归一化（转小写、剔除「副本/备份/copy/old」等噪声词、去掉符号）后包含 `tacz`
   - 或与 `tacz` 编辑距离 ≤1（`tac`、`taczs`）
   - 或四个字符完全相同只是顺序颠倒（`tazc`）
   - 或直接写着 `绝对彼方` / `jueduibifang` / `absoluteopponent` / `ocle`
   - 遍历时跳过 `mods`、`saves`、`config`、`logs`、`libraries`、`assets`、**`ocle_guard_trash`** 等目录
3. **内容指纹**：名字完全无关，但目录里有 ocle 枪包特征 → 也纳入

### 2.2 命中条件（任一即命中）

| 类型 | 证据 |
| --- | --- |
| 枪包目录 | 根部 `gunpack.meta.json` 的 `namespace` 为 `ocle` |
| 枪包目录 | 存在 `assets/ocle/` 资源目录 |
| 枪包 zip | zip 内 `gunpack.meta.json` 的 `namespace` 为 `ocle`（只读中央目录，不解压全包） |
| 枪包 zip | zip 内存在 `assets/ocle/` 条目 |

判定作用在「候选目录自身」和「它的直接子项（子目录 / zip）」两层上，
所以 `.minecraft/tacz/绝对彼方 v1.11.0.zip`、`.minecraft/tacz/ocle/`、`.minecraft/versions/1.20.1-forge/tacz/*.zip` 都能命中。

### 2.3 不触发的情况

- `.minecraft/tacz` 存在，但里面全是别的枪包 → **静默放行**（不误伤）
- 目录名叫 `tacz` 但是空的 → 静默放行
- 枪包 `namespace` 不是 `ocle` → 静默放行

---

## 三、软删除：文件去了哪、怎么拿回来

**没有任何文件被删除。** 命中的产物被整体 `Files.move` 到：

```
.minecraft/ocle_guard_trash/<yyyy-MM-dd_HH-mm-ss>/
├── 绝对彼方 v1.11.0.zip      # 原文件，字节数与时间戳不变
├── manifest.txt             # 原始路径 / 文件数 / 字节数 / 判定证据
└── RESTORE.txt              # 双语恢复说明
```

`Files.move` 在同一分区内只改目录项，**不复制、不重写文件**，所以 72MB 的枪包移动耗时是毫秒级，
内容、大小、修改时间全部保持原样。

恢复只要两步：完全退出游戏 → 把 `ocle_guard_trash/<时间戳>/` 里的东西复制回 `manifest.txt` 里记的原始路径。
`RESTORE.txt` 里逐条列出了「现在位置 → 原始位置」。

### 3.1 移动护栏（为什么它不会把你的存档搬走）

`ArtifactQuarantine` 在动手前过四道检查，任一不过就拒绝并在日志里写明原因：

1. 目标必须在**游戏目录内**（防路径穿越 `..`）
2. 目标**不能是游戏目录本身**
3. 路径中不能出现 `mods` / `config` / `saves` / `logs` / `libraries` / `resourcepacks` / `shaderpacks` / `screenshots` / `coremods` / **`ocle_guard_trash`**
4. 目标是**符号链接**时直接拒绝

另外：回收站目录本身在扫描阶段就被跳过，所以下次启动**不会**把隔离区里的枪包再抓一遍（否则会无限套娃）。

---

## 四、语言：第二语言怎么"生效"

| 项 | 说明 |
| --- | --- |
| 支持语言 | 简体中文 `zh_cn`（默认）、英语 `en_us` |
| 语言文件 | `assets/ocle_uninstall_crash/lang/zh_cn.json`、`en_us.json`（35 个键，键集合完全一致） |
| 判定来源 | `.minecraft/options.txt` 里的 `lang:` 行 |
| 回退规则 | 文件缺失、无 `lang:` 行、语言不支持（如 `ja_jp`）→ 简体中文 |

**为什么不用 `I18n.get()`**：崩溃发生在 mod 构造阶段，那时 Minecraft 客户端还没创建，
语言管理器、资源包都没加载，`I18n` 拿不到任何字符串。而玩家的语言选择早就写在 `options.txt` 里，
所以直接读文件是这个阶段唯一可靠、也最容易验证的做法。

**切换效果**：日志、崩溃报告、`RESTORE.txt` 的正文语言随玩家设置切换。
唯一例外是契约句「请勿同时加载此mod和绝对彼方」——它要求原样出现在日志里，所以**无论什么语言都会输出**，
另一语言紧随其后（中文环境下先中文后英文，英文环境下先英文后中文）。

> 注：Forge 1.20.1 不会按语言本地化 `mods.toml` 里的 `displayName`，所以模组列表里的名字固定为中文。
> 这是 Forge 侧的既有限制，不是本模组没做。

---

## 五、日志与崩溃报告长什么样

`logs/latest.log`（同时打到 stderr，防止日志被过滤）：

```
[OCLE-Guard] 共检测到 1 处绝对彼方产物，开始移入回收站：
[OCLE-Guard] 命中: .minecraft\tacz\绝对彼方 v1.11.0.zip
[OCLE-Guard] 来源: 标准路径 .minecraft/tacz · 子文件 | 证据: zip:gunpack.meta.json -> namespace=ocle
[OCLE-Guard] 已移入回收站: .minecraft\ocle_guard_trash\2026-10-23_22-47-39\绝对彼方 v1.11.0.zip（1 个文件 / 0 个目录 / 76136219 字节，56 ms）
[OCLE-Guard] 恢复说明: .minecraft\ocle_guard_trash\2026-10-23_22-47-39\RESTORE.txt
[OCLE-Guard] 已注销自身事件监听器 1 个。
[OCLE-Guard] 崩溃报告: .minecraft\crash-reports\crash-2026-10-23_22.47.39-ocle-guard.txt
==================================================
  请勿同时加载此mod和绝对彼方
  Do not load this mod together with Absolute Opponent
==================================================
```

`crash-reports/crash-<时间>-ocle-guard.txt` 由模组自己写（纯 JDK 拼文本，不碰 `CrashReport` 单例，避免二次异常盖掉真实原因），
内容包括：命中清单、每条证据、移动结果、回收站路径、恢复入口、注销数量、系统信息、调用栈，以及那句提示语。

---

## 六、目录结构

```
ocle-uninstall-crash/
├── build.gradle                      # ForgeGradle 6
├── settings.gradle                   # 插件仓库（maven.minecraftforge.net）
├── gradle.properties                 # 版本号/映射/模组信息
├── .gitignore / .gitattributes       # 忽略项与换行符规则
├── README.md / BUILD.md / GITHUB.md  # 说明 / 构建教程 / 上传流程
├── LICENSE                           # MIT
├── dist/
│   └── ocle_uninstall_crash-1.0.0.jar  # 已构建好的可安装 jar（51,391 字节）
├── tools/
│   ├── OfflineSelfTest.java          # 离线自测（只依赖 JDK，不含 Forge）
│   ├── run-selftest.bat              # 双击即可跑
│   ├── selftest-report.txt           # 自测结果（UTF-8，51 项全绿）
│   ├── build-offline.bat             # 不装 Gradle 的一键打包脚本
│   └── JarVerify.java                # jar 链接自检（在真实 Forge 库上加载类）
└── src/main/
    ├── java/cn/jikuai/ocleuninstall/
    │   ├── OcleUninstallCrash.java   # @Mod 入口，构造即判定
    │   ├── ConflictReactor.java      # 读语言→扫描→软删除→自清→崩溃
    │   ├── SelfPurge.java            # 注销自身事件监听器
    │   ├── CrashReportWriter.java    # 自写崩溃报告（跟随语言）
    │   └── core/                     # 纯 JDK，可脱离 Forge 单独编译测试
    │       ├── GuardConst.java       # 全部硬编码规则（无配置文件）
    │       ├── NameMatch.java        # 目录名归一化/相似度/编辑距离
    │       ├── GunpackProbe.java     # 目录与 zip 的 ocle 指纹探测
    │       ├── Evidence.java         # 判定证据
    │       ├── Artifact.java         # 待处理产物
    │       ├── ArtifactScanner.java  # 三通道扫描 + 去重
    │       ├── ArtifactQuarantine.java # 带护栏的软删除（移动）
    │       ├── Lang.java             # 语言枚举 + 读 options.txt
    │       ├── LangTable.java        # 扁平 JSON 语言表（无 Gson 依赖）
    │       └── RecoveryGuide.java    # 生成双语 RESTORE.txt
    └── resources/
        ├── META-INF/mods.toml        # 模组元数据（含中文显示名）
        ├── pack.mcmeta               # pack_format 15
        ├── icon.png                  # 图标（256×256）
        └── assets/ocle_uninstall_crash/lang/{zh_cn,en_us}.json
```

`core` 包刻意不 import 任何 Forge 类：所以它能用普通 `javac` 编译，也就能在没有网络、没有 Forge 依赖的环境里被真跑一遍。

---

## 七、构建

**详细教程见 [BUILD.md](BUILD.md)**（mod jar 的组成、ForgeGradle 流程、reobf 是什么、不装 Gradle 的离线编译法）。

```bash
# A. 标准做法（需联网拉 ForgeGradle 依赖）
gradle build                 # 产物 build/libs/ocle_uninstall_crash-1.0.0.jar

# B. 离线做法（复用本机 .minecraft 里的 Forge 库，3 秒出包）
tools\build-offline.bat      # 产物 dist\ocle_uninstall_crash-1.0.0.jar
```

已产出的 jar：51,391 字节，32 个条目（含语言资源与 256×256 图标），class 版本 61（Java 17），
`JarVerify` 在真实 Forge `1.20.1-47.4.21` 上通过 9 项检查。

> 路线 B 之所以不需要 ForgeGradle 的 reobf，是因为本模组**没有引用任何 `net.minecraft.*` 类**。
> 一旦你给模组加了方块/物品，就必须回到路线 A，否则运行时 `NoSuchMethodError`。

---

## 八、离线自测（不需要 Forge、不需要联网）

```bash
tools\run-selftest.bat
```

自测会：用**真实的 72MB 枪包**做指纹识别、在临时目录搭出多种 `.minecraft` 布局、
把命中项移进回收站、核对字节数不变、验证「再次扫描不会命中回收站」、模拟从回收站复制回原位、
检查两种语言的键集合是否一致、以及越界移动是否被拒绝。

实测结果（`tools/selftest-report.txt`）：

```
---- 语言检测与语言表 ----
  [PASS] options.txt lang:en_us -> EN_US
  [PASS] 两种语言的键集合一致（zh=35, en=35）
---- 软删除：移入回收站 + 可恢复 ----
  [PASS] 回收站里 zip 字节数不变（数据未丢）
  [PASS] 再次扫描不再命中回收站内容
  [PASS] 从回收站复制回原位成功，内容一致
...
通过: 51    失败: 0
```

---

## 九、安装、触发与恢复

1. 把 jar 放进 `mods/`
2. 只要游戏目录里还有绝对彼方 → 启动即崩，枪包被移进 `ocle_guard_trash/`，`crash-reports/` 里能看到原因
3. 想正常游戏：**删掉本模组的 jar**
4. 想把枪包拿回来：按 `ocle_guard_trash/<时间戳>/RESTORE.txt` 复制回原路径
5. 确认不再需要了：直接清空 `ocle_guard_trash/`

---

## 十、已知限制（诚实说明）

- **不能真正注销别的模组的注册项**。1.20.1 的 `Registry` 在加载完成后就冻结了，枪包内容又存在 TaCZ 自己的动态注册表里；
  所以「卸载绝对彼方」只能在**文件层面**完成——把它移出加载路径，下次启动 TaCZ 自然读不到。
- 本模组自身不注册方块/物品/配方，唯一产物是 1 个事件监听器；崩溃前会把它从事件总线注销并记录条数。
- 扫描深度上限 4，并跳过 `mods`/`saves` 等目录。如果玩家把枪包塞进 `mods/` 或存档目录里，本模组不会去动（刻意的安全边界）。
- 只做了客户端/服务端通用的 `FMLPaths.GAMEDIR` 判定，没区分物理端；服务端目录里同样会扫、同样会移。
- 语言只在启动那一刻读一次 `options.txt`，游戏内切换语言不会影响已写出的日志（进程已经崩了）。

---

## 十一、许可

MIT，见 [LICENSE](LICENSE)。
