# 如何构建这个 mod 的 jar

这份文档讲两件事：**Forge mod jar 到底由什么组成**，以及 **两条能把它造出来的路**。
路线 A 是官方标准做法（要联网），路线 B 是不装 Gradle、拿本机 `.minecraft` 里现成的 Forge 库直接编（本工程已实测通过，产物在 `dist/`）。

---

## 0. 先搞清楚：一个 Forge mod jar 里必须有什么

| 内容 | 是否必需 | 说明 |
| --- | --- | --- |
| `META-INF/mods.toml` | **必需** | Forge 靠它认领 jar。没有它，jar 就是个普通 jar，游戏根本不看 |
| `cn/.../*.class` | 必需 | 你的代码 |
| `META-INF/MANIFEST.MF` | 必需 | `jar` 工具会自动生成一个最小版本 |
| `pack.mcmeta` | 建议 | 让 jar 同时被当作资源包/数据包；`pack_format` 必须和 MC 版本匹配（1.20.1 = **15**） |
| `icon.png` + `mods.toml` 里的 `logoFile` | 可选 | 模组列表里显示的图标 |
| `assets/<modid>/...`、`data/<modid>/...` | 可选 | 贴图、模型、语言文件、配方 |
| `*.mixins.json` + `refmap.json` | 可选 | 只有用 Mixin 才需要 |

`mods.toml` 里三样东西必须对得上，否则启动就报错：

```toml
modLoader="javafml"          # 1.20.1 用 javafml
loaderVersion="[47,)"        # Forge 47.x
[[mods]]
modId="ocle_uninstall_crash" # 只能小写字母/数字/下划线
version="1.0.0"
```

---

## 1. 两条路线怎么选

| | 路线 A：ForgeGradle | 路线 B：离线 javac |
| --- | --- | --- |
| 适用 | 任何 mod，包括用 Mixin、引用 Minecraft 类的 | 只引用 Forge API、不碰 `net.minecraft.*` 的 mod |
| 需要联网 | 是（首次约 600MB～1GB） | 否（用本机 `.minecraft/libraries` 里现成的 jar） |
| 耗时 | 首次 10–20 分钟，之后增量几秒 | 3 秒 |
| 需要 reobf | 是（自动） | 不需要（见 3.1） |
| 调试运行 | 有 `runClient` | 只能丢进 `mods/` 试 |

**reobf 是什么**：Minecraft 正式版里的类名/方法名是混淆过的（SRG 名，如 `m_12345_`）。你在开发环境用可读名（`official` 映射）写代码，编译出来的字节码引用的是可读名，直接进游戏会 `NoSuchMethodError`。ForgeGradle 的 `reobfJar` 任务负责把字节码里的名字重新映射回 SRG。
**所以：只要你 import 了任何 `net.minecraft.*` 的类或成员，就必须走路线 A。**
本模组一个 Minecraft 类都没用（只用 `net.minecraftforge.*` 和 log4j），所以路线 B 合法且产物可用。

---

## 2. 路线 A：ForgeGradle（标准做法）

### 2.1 环境

```bat
java -version     :: 必须是 17（本机：C:\Program Files\Java\jdk-17）
javac -version    :: 17.0.7
```

需要：**JDK 17**、**Gradle 8.2+**、**能访问 `maven.minecraftforge.net`**、至少 2GB 空闲磁盘。

> 1.20.1 必须用 JDK 17。用 21 或 26 会在 ForgeGradle 的某些任务上报 `Unsupported class file major version`。

### 2.2 项目结构（本工程已经具备）

```
ocle-uninstall-crash/
├── build.gradle          # ForgeGradle 6，声明 minecraft { mappings ... } 和依赖
├── settings.gradle       # 插件仓库：maven.minecraftforge.net
├── gradle.properties     # minecraft_version / forge_version / mod_id ...
└── src/main/java|resources
```

### 2.3 构建

```bat
cd /d C:\Users\Administrator\WorkBuddy\2026-10-23-19-40-50\ocle-uninstall-crash

gradle build
:: 或者先生成 wrapper（本工程没带 gradle-wrapper.jar）
gradle wrapper --gradle-version 8.2.1
gradlew build
```

产物：`build/libs/ocle_uninstall_crash-1.0.0.jar`

首次构建 Gradle 会依次做这些事，看起来像卡住，其实在下载：

1. 拉 `net.minecraftforge.gradle:ForgeGradle:6.0.x` 插件
2. 拉 `net.minecraftforge:forge:1.20.1-47.2.0:userdev`
3. 从 Mojang 拉 client/server jar 和 libraries
4. 反编译 Minecraft 生成可读源码（`-Xmx3G` 就是给这步的）
5. 生成 mappings、编译、reobf

### 2.4 开发期直接跑游戏

```bat
gradle runClient
```

工作目录是 `run/`，那里的 `.minecraft` 布局是独立的，适合先放一个假的 `tacz` 目录试触发。

### 2.5 连不上 forge maven 怎么办（本机就是这个情况）

先确认是不是真的不通：

```bat
curl -I https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml
```

- **超时/连接重置** → 走代理。写进 `gradle.properties`：

```properties
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=7890
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=7890
```

- **只想换个网络**：手机热点通常能过。
- **别指望镜像**：阿里云/腾讯云镜像只代理 Maven Central，`forge:userdev` 和 `mcp_config` 只在官方仓库，换镜像依旧 404。
- **最快的解法**：直接用路线 B——用你已经装好的 Forge 库编译，不需要网络。

### 2.6 常见报错对照

| 报错 | 原因 | 处理 |
| --- | --- | --- |
| `invalid source release: 17` | 用 JDK 8/11 在跑 Gradle | 换 JDK 17，或 `org.gradle.java.home` 指过去 |
| `Unsupported class file major version 65` | 用 JDK 21 编译 ForgeGradle 6 | 降回 17 |
| `Could not resolve net.minecraftforge:forge:1.20.1-...` | 网络 / 代理没生效 | 见 2.5 |
| `Task 'reobfJar' not found` | ForgeGradle 插件没加载成功 | 检查 `settings.gradle` 的 pluginManagement 仓库 |
| 游戏里报 `Missing or unsupported mandatory dependencies` | `mods.toml` 的 `versionRange` 太窄 | 放宽，例如 `[47,)` |
| 装进游戏后模组列表里看不到 | `META-INF/mods.toml` 没打进 jar | `jar tf` 确认路径，检查 `processResources` |

---

## 3. 路线 B：不装 Gradle，直接编（本工程实测通过）

### 3.1 为什么不需要 mappings / reobf

本模组引用的全部外部类型：

```
net.minecraftforge.fml.common.Mod
net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent
net.minecraftforge.fml.loading.FMLPaths
net.minecraftforge.eventbus.api.IEventBus / SubscribeEvent
org.apache.logging.log4j.Logger / LogManager
```

这些类在正式版里**名字没有被混淆**（Forge 自己的 API 不在 SRG 映射范围内），所以「开发环境编出来的字节码」和「游戏里能加载的字节码」是同一份。
证据：编译时只要 6 个 jar，`net.minecraft` 一个都没出现在 classpath 里。

### 3.2 一键构建

```bat
tools\build-offline.bat
```

脚本会：找 JDK 17 → 找 `.minecraft` 里的 Forge 库 → javac → 塞资源 → 写 MANIFEST → 打包到 `dist\`。

找不到 `.minecraft` 时手动指定：

```bat
tools\build-offline.bat "D:\怀民\.minecraft"
```

### 3.3 手动五步（想理解每一步就看这里）

**第 1 步：凑齐 6 个 jar**（版本号取你 `.minecraft` 里实际有的）

```
libraries\net\minecraftforge\forge\1.20.1-47.4.21\forge-1.20.1-47.4.21-universal.jar
libraries\net\minecraftforge\javafmllanguage\1.20.1-47.4.21\javafmllanguage-1.20.1-47.4.21.jar
libraries\net\minecraftforge\fmlloader\1.20.1-47.4.21\fmlloader-1.20.1-47.4.21.jar
libraries\net\minecraftforge\fmlcore\1.20.1-47.4.21\fmlcore-1.20.1-47.4.21.jar
libraries\net\minecraftforge\eventbus\6.2.33\eventbus-6.2.33.jar
libraries\org\apache\logging\log4j\log4j-api\2.19.0\log4j-api-2.19.0.jar
```

哪个类在哪个 jar 里：`@Mod` 和 `FMLJavaModLoadingContext` 在 **javafmllanguage**，`FMLPaths` 在 **fmlloader**，`FMLLoadCompleteEvent` 在 **forge-universal**，`IEventBus` 在 **eventbus**。

**第 2 步：编译**

```bat
javac -encoding UTF-8 --release 17 -cp "<上面6个jar用分号连起来>" ^
      -d .build-offline\classes2 ^
      src\main\java\cn\jikuai\ocleuninstall\*.java ^
      src\main\java\cn\jikuai\ocleuninstall\core\*.java
```

**第 3 步：把资源放进 classes 目录**

```bat
mkdir .build-offline\classes2\META-INF
copy src\main\resources\META-INF\mods.toml .build-offline\classes2\META-INF\
copy src\main\resources\pack.mcmeta      .build-offline\classes2\
copy src\main\resources\icon.png         .build-offline\classes2\
xcopy /E /I /Y src\main\resources\assets .build-offline\classes2\assets
```

> `assets` 目录别漏：语言文件在里面，漏了不会崩，但日志会变成键名（`log.clean` 这种）。

**第 4 步：写 MANIFEST**（`Implementation-Version` 只是给自己看的，mods.toml 里已经写死 1.0.0）

```
Manifest-Version: 1.0
Specification-Title: ocle_uninstall_crash
Implementation-Title: ocle-uninstall-crash
Implementation-Version: 1.0.0
```

**第 5 步：打包**

```bat
mkdir dist
jar --create --file dist\ocle_uninstall_crash-1.0.0.jar ^
    --manifest .build-offline\MANIFEST.MF ^
    -C .build-offline\classes2 .
```

### 3.4 校验（别跳过）

```bat
jar --list --file dist\ocle_uninstall_crash-1.0.0.jar
```

必须看到这 4 类条目，缺一个游戏就不认：

```
META-INF/MANIFEST.MF
META-INF/mods.toml
pack.mcmeta
assets/ocle_uninstall_crash/lang/en_us.json
cn/jikuai/ocleuninstall/OcleUninstallCrash.class
```

再做一次**链接自检**：用真实 Forge 库加载 jar 里的类（`initialize=false`，不会触发删除逻辑）：

```bat
javac -d .build-offline\verify tools\JarVerify.java
java -cp .build-offline\verify JarVerify dist\ocle_uninstall_crash-1.0.0.jar <6个jar>
```

本工程的实测输出：

```
[PASS] 模组类可加载: cn.jikuai.ocleuninstall.OcleUninstallCrash
[PASS] @Mod 注解可读，value=ocle_uninstall_crash
[PASS] 存在无参构造器（Forge 实例化入口）
[PASS] 监听方法签名正确: onLoadComplete(FMLLoadCompleteEvent)
[PASS] 监听方法带 @SubscribeEvent
[PASS] FMLPaths 可解析（游戏目录入口）
[PASS] core 包随 jar 一起发布: cn.jikuai.ocleuninstall.core.ArtifactScanner
[PASS] 语言资源 zh_cn 在 jar 内
[PASS] 语言资源 en_us 在 jar 内

JAR VERIFY OK
```

这三项检查（`jar --list`、JarVerify、`class major version = 61`）能覆盖 90% 的「装进游戏里没反应」问题。

### 3.5 这条路的边界

- 一旦你 `import net.minecraft.*`（哪怕只是 `net.minecraft.world.item.Item`），**立刻回路线 A**，否则运行时报 `NoSuchMethodError` / `NoClassDefFoundError`。
- 路线 B 不能 `runClient` 调试，改一次丢一次 jar。
- 你本机没有的 Forge 版本，路线 B 编不了（它依赖 `.minecraft/libraries` 里的实际文件）。

---

## 4. 装进游戏实测

1. 先备份你的枪包（虽然本模组只做移动、不删数据，备份仍然是好习惯）：

```bat
xcopy /E /I /Y "D:\xxx\.minecraft\tacz" "D:\backup\tacz_备份"
```

2. 把 `dist\ocle_uninstall_crash-1.0.0.jar` 丢进 `.minecraft\mods\`
3. 启动 Forge 1.20.1
4. 预期结果：
   - `logs/latest.log` 里出现 `请勿同时加载此mod和绝对彼方`
   - `crash-reports\crash-<时间>-ocle-guard.txt` 记录命中路径、回收站路径与字节数
   - 弹「Mod loading has failed」，进不了主菜单
   - `tacz` 里的 ocle 枪包已被**移动**到 `.minecraft\ocle_guard_trash\<时间戳>\`
5. 想恢复：删掉本模组 jar，按 `ocle_guard_trash\<时间戳>\RESTORE.txt` 把文件搬回原路径

---

## 5. 发布前检查清单

- [ ] `mods.toml` 的 `modId` 只含小写字母/数字/下划线
- [ ] `version` 与文件名、`gradle.properties` 的 `mod_version` 一致
- [ ] `logoFile` 指向的 png **在 jar 根目录**（不是 `assets/xxx/` 下）
- [ ] `pack_format` 与 MC 版本匹配（1.20.1 = 15）
- [ ] 所有 `.class` 的 major version = 61（Java 17）
- [ ] `versionRange` 覆盖玩家实际用的 Forge 版本
- [ ] `jar tf` 里没有 `.log`、`sources.txt`、IDE 配置文件
- [ ] 发布前先把 `dist/` 里的 jar 装进一个干净客户端跑一遍

---

## 6. 本工程的实际构建记录

| 项 | 值 |
| --- | --- |
| 产物 | `dist/ocle_uninstall_crash-1.0.0.jar` |
| 大小 | 51,391 字节 |
| 条目 | 32（15 个 class + 2 个语言文件 + mods.toml + pack.mcmeta + icon.png + MANIFEST + 目录项） |
| 编译依据 | `D:\怀民\.minecraft\libraries` 里的 Forge `1.20.1-47.4.21` |
| class 版本 | 61（Java 17） |
| 链接自检 | JarVerify 9/9 PASS |
| 核心自测 | OfflineSelfTest 51/51 PASS |
| 构建方式 | 路线 B（离线 javac） |

### 6.1 踩过的坑：jar 变成 13 字节

第一版 `build-offline.bat` 结尾写了一句：

```bat
echo BUILD OK  ->  %DIST%\%MODID%-%MODVER%.jar
```

`->` 里的 `>` 在 cmd 里是**输出重定向**。于是这行不是在"打印"，而是把 `BUILD OK  -`
这 13 个字节**写进了刚打好的 jar**，把它覆盖成了文本文件——`jar --list` 随即报
`zip END header not found`。

修法有三层，缺一不可：

1. `echo` / `rem` 文本里**绝不出现 `>` 或 `<`**（要写就写成 `-` 或 `^>`）
2. 打包后做**体积哨兵**：小于 30KB 直接判失败（正常产物 50KB+，图标换小也不会误判）
3. 打包后再跑一次 `jar --list`，读不出来就退出码非零

另外两处顺带修掉的问题：

- 之前把工作目录命名为 `%TMP%`，覆盖了系统临时变量，JVM 的 `hsperfdata_*` 都跑进项目里了 → 改用 `%WORK%`
- 版本选择原来用 `dir /o-n`（按名字倒序），会选中 `47.4.6` 而不是 `47.4.21`；改成 `/o-d` 并逐个校验四个 Forge jar 是否齐全
