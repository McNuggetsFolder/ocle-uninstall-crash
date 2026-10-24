# 上传到 GitHub 的完整流程

针对本工程（`ocle-uninstall-crash/`）的实操版：从零建仓，到 `git push` 成功，再到发布 Release。
所有命令都在 **项目根目录** 执行（即 `ocle-uninstall-crash` 这一层）。

---

## 0. 前置检查（只做一次）

```bash
git --version                 # 没装就去 https://git-scm.com/download/win
git config --global user.name  "你的名字"
git config --global user.email "你的邮箱"

# Windows 中文文件名显示成 \344\270\255 这种转义时，关掉 quotepath
git config --global core.quotepath false
```

然后到 GitHub 网页端 **New repository**：

- Repository name：`ocle-uninstall-crash`
- 可见性：Public（模组要给人下载，Private 的仓库链接 MC百科那边没法核对）
- **不要**勾选 "Add a README file / .gitignore / license"——本地已经有了，勾了会导致首次 push 冲突

---

## 1. 仓库初始化

```bash
cd /d C:\Users\Administrator\WorkBuddy\2026-10-23-19-40-50\ocle-uninstall-crash

git init
git branch -M main            # 统一用 main，别留 master
git status                    # 先看清楚：有哪些文件会被提交
```

**这一步最关键**：`git status` 里如果出现 `.build-offline/`、`dist/*.jar`、`tools/build-check2/`，
说明 `.gitignore` 没生效，先修 `.gitignore` 再继续。

---

## 2. 添加与提交

```bash
# 看看到底会加进去什么（--dry-run 不会真的 add）
git add --dry-run .

# 确认无误后全量添加
git add .

# 再看一次暂存区
git status --short

# 提交
git commit -m "feat: 检测绝对彼方枪包并移入回收站后终止启动"
```

想拆成几个提交更清楚，可以分三步：

```bash
git add src build.gradle settings.gradle gradle.properties
git commit -m "feat: 模组源码与构建配置"

git add README.md BUILD.md GITHUB.md LICENSE .gitignore .gitattributes
git commit -m "docs: 说明、构建与上传流程"

git add tools
git commit -m "test: 离线自测与 jar 链接校验"
```

---

## 3. 关联远程仓库

```bash
# HTTPS（简单，推送时要 token）
git remote add origin https://github.com/<你的用户名>/ocle-uninstall-crash.git

# 或者 SSH（配好 key 后免密）
# git remote add origin git@github.com:<你的用户名>/ocle-uninstall-crash.git

git remote -v                 # 确认 fetch/push 两行都指向正确地址
```

如果之前加错过：

```bash
git remote set-url origin <正确地址>
# 或
git remote remove origin && git remote add origin <正确地址>
```

---

## 4. 推送到远端

```bash
git push -u origin main
```

`-u` 会把 `origin/main` 设为上游，之后直接 `git push` 即可。

### 认证（2021 年之后不能用账号密码）

- **HTTPS**：去 GitHub → Settings → Developer settings → Personal access tokens → 生成一个带 `repo` 权限的 token，
  密码框里粘 token。
- **SSH**：`ssh-keygen -t ed25519 -C "你的邮箱"`，把 `~/.ssh/id_ed25519.pub` 贴到 GitHub → SSH keys，
  然后 `ssh -T git@github.com` 看到 `Hi xxx!` 就成了。
- **gh CLI**：`gh auth login` 一条命令搞定，之后 `gh repo create` 能全自动建仓。

---

## 5. 发布 jar（重要）

**不要把 jar 提交进仓库**。仓库只放源码，二进制走 Releases：

1. 网页端 → Releases → Draft a new release
2. Tag：`v1.0.0`，Target：`main`
3. 标题：`v1.0.0`
4. 上传 `dist/ocle_uninstall_crash-1.0.0.jar`
5. 描述里写清：支持 1.20.1 / Forge 47.x、会移动枪包到 `ocle_guard_trash`、如何恢复

命令行也行：

```bash
gh release create v1.0.0 dist/ocle_uninstall_crash-1.0.0.jar --title "v1.0.0" --notes "首个版本"
```

Release 的下载链接（`.../releases/download/v1.0.0/xxx.jar`）就是投 MC百科时要填的「官方下载地址」。

---

## 6. 上传时要注意的文件与忽略项

### 6.1 必须提交

| 文件 | 原因 |
| --- | --- |
| `src/main/java/**` | 源码 |
| `src/main/resources/**` | `mods.toml`、`pack.mcmeta`、`icon.png`、`assets/*/lang/*.json` |
| `build.gradle`、`settings.gradle`、`gradle.properties` | 别人要能复现构建 |
| `README.md`、`BUILD.md`、`GITHUB.md` | 说明与文档 |
| `LICENSE` | 开源协议，MC百科会看 |
| `tools/**` | 自测脚本（不含 `out/`、`build-check*/`） |
| `.gitignore`、`.gitattributes` | 协作基础 |

### 6.2 绝对不能提交

| 路径 | 原因 |
| --- | --- |
| `.build-offline/`、`build/`、`out/`、`run/` | 编译产物，几十 MB，且每次不一样 |
| `dist/*.jar` | 二进制走 Release；提交进仓库会让仓库迅速膨胀 |
| `tools/build-check*/`、`tools/out/`、`tools/selftest-compile.log` | 自测产生的临时目录与日志 |
| `*.log`、`crash-reports/` | 运行产物 |
| `.minecraft`、`options.txt`、`ocle_guard_trash/` | 玩家本地数据，可能含隐私（账号、路径） |
| 任何 72MB 的枪包 zip | GitHub 单文件硬上限 100MB，且涉嫌分发他人作品 |

`.gitignore` 已经覆盖了上面绝大部分，但**提交前必须自己 `git status` 复核一遍**——
`.gitignore` 对已经被 `git add` 过的文件无效。

### 6.3 Windows 特有的两个坑

1. **换行符**：不加约束的话 Git 会在提交时把 CRLF 转成 LF，别人 clone 下来的 `.bat` 可能跑不了。
   本工程已附带 `.gitattributes`：

```
* text=auto eol=lf
*.bat text eol=crlf
*.jar binary
*.png binary
```

2. **中文文件名**：`assets/ocle_uninstall_crash/lang/zh_cn.json` 是纯 ASCII，没问题；
   但如果以后加了中文名文件，记得 `git config --global core.quotepath false`，否则 `git status` 里全是八进制转义，容易看漏。

### 6.4 想提交大文件（不推荐）

真需要（比如示例枪包），用 Git LFS：

```bash
git lfs install
git lfs track "*.zip"
git add .gitattributes
```

但本项目**不建议**提交任何枪包——那是别人的作品。

---

## 7. 提交前自检清单

```bash
git status --short            # 只应出现你预期的文件
git ls-files | wc -l          # 大致数量（本工程约 30 个）
git ls-files | grep -E "\.jar$|\.class$|build|\.log$"   # 应该没有任何输出
```

- [ ] `.gitignore` 已包含 `.build-offline/`、`dist/`、`tools/build-check*`
- [ ] `git ls-files` 里没有 `.class`、`.jar`、`.log`
- [ ] `LICENSE` 是 MIT 且年份/作者正确
- [ ] `README.md` 里没有本地绝对路径（`C:\Users\Administrator\...`）
- [ ] `gradle.properties` 里没有 token、密码
- [ ] 仓库是 Public，且 README 首屏能看懂这是干什么的（会有玩家直接下载）

---

## 8. 常见报错

| 报错 | 原因 | 处理 |
| --- | --- | --- |
| `remote origin already exists` | 重复添加 | `git remote set-url origin <地址>` |
| `Updates were rejected (non-fast-forward)` | 远端有你本地没有的提交（建仓时勾了 README） | `git pull --rebase origin main` 后再 push |
| `Authentication failed` | 用了账号密码 | 换 Personal Access Token 或 SSH |
| `LF will be replaced by CRLF` | 换行符转换警告 | 加 `.gitattributes`，或忽略（不影响功能） |
| `file is 123.00 MB; this exceeds GitHub's file size limit` | 提交了大文件 | `git rm --cached <文件>`，加进 `.gitignore`，再 `git commit --amend` |
| `fatal: not a git repository` | 不在项目根目录 | `cd` 到 `ocle-uninstall-crash` 再执行 |

---

## 9. 后续更新

```bash
git add -A
git commit -m "fix: 修正 xxx"
git push
```

发新版本时：改 `gradle.properties` 的 `mod_version`、`mods.toml` 的 `version`，重新构建，
再 `gh release create v1.0.1 dist/xxx-1.0.1.jar`。
