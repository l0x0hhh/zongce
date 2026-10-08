# 更新镜像托管：现状、硬约束与迁移

> 起因：2026-10-08 发现线上更新端点停在 `1.5.2`，而发版流水线 14 步全绿、每日兜底也天天 success。
> 根因见 [VERSION_HISTORY.md](../../VERSION_HISTORY.md) 的「交付事故与修复」：Netlify 账号额度用尽，
> **生产部署被暂停** —— 推送到达后 Deploys 里那条记录直接是 Skipped，站点停在最后一次成功的部署上。

## 一、先看这个：镜像地址是编译进包里的

App 只认两个更新来源，**两个都是构建期确定的**：

| 来源 | 地址从哪来 | 装完之后还能改吗 |
| --- | --- | --- |
| 镜像清单 | `BuildConfig.UPDATE_MANIFEST_URL` ← Gradle 属性 `updateManifestUrl` ← workflow 的 `LIVE_MANIFEST_URL` | ❌ 不能 |
| GitHub Release | `UpdateChecker.GITHUB_REPOSITORY`，代码里写死 | ❌ 不能 |

实现在 `app/src/main/java/com/zongce/app/update/UpdateChecker.kt` 的 `readMirror()` / `readGitHubRelease()`。

**这决定了迁移顺序：**

- 换了托管、改了 `LIVE_MANIFEST_URL`，**只对之后构建的包生效**。
- 已装机的包（比如 v1.5.2 那批）只会去查 **Netlify + GitHub**，永远不知道新址存在。
- 而最需要镜像的恰恰是 **GitHub 不通**的那批用户 —— 换托管**救不到他们**，因为他们的包里没有新地址。

所以「换托管」的正确含义是 **「下一版起不再依赖 Netlify」**，不是「马上把 Netlify 摘掉」。顺序只能是：

1. 让 Netlify 恢复正常（升级 / 等计费周期 → Deploys 面板点 Retry）。
2. 发一版，把已装机用户带到含新地址的版本上。
3. 之后才把镜像切到新托管；Netlify 可保留作冗余（App 并发查两源、取版本更高者）。

> 若跳过第 1、2 步直接切换：GitHub 可达的用户仍能更新（GitHub 源是新的），
> 但 **GitHub 不可达的用户会被旧 Netlify 上的旧版本永久骗成"已是最新"**。

## 二、候选托管（2026-10-08 核实）

关键区分：**App 的更新链路根本不看 `Content-Type`**（`HttpURLConnection` 读字节流），
只有**浏览器从产品页下载**才需要 `application/vnd.android.package-archive`。
这两件事不必由同一个托管承担 —— 想清楚这点，可选空间大很多。

| 方案 | 国内速度 | 需要备案 | `.apk` 的 Content-Type | 备注 |
| --- | --- | --- | --- | --- |
| Netlify（现状） | 一般 | 否 | ✅ 可设（`netlify.toml` / `_headers`） | 免费额度用尽即暂停生产部署 |
| **Gitee**（旧方案，代码还在 `release.yml` 里） | **快**（v1.3.5 实测清单 1.2s） | 否 | ❌ 附件 CDN 返回 `application/zip` | **对更新链路够用**（App 不看）；浏览器下载会存成 `.zip` |
| 腾讯云 COS / 阿里云 OSS | **最快** | **要** | ✅ 可设 | ⚠️ 2024-01-01 后创建的存储桶，用**默认域名**访问 `.apk` 直接 **403 `DownloadForbidden`**，必须自定义域名 → 即必须备案 |
| Cloudflare Pages | ⚠️ 不稳定 | 否 | ✅ `_headers` 可覆盖（与 netlify.toml 同源语法） | 免费、无额度焦虑；但社区普遍反馈 `*.pages.dev` 国内经常打不开 |
| GitHub Pages | 差 | 否 | ⚠️ | 与 GitHub Release 同源，起不到镜像作用 |

## 三、推荐：把两个角色拆开

- **App 更新镜像（清单 + APK 源）→ Gitee**。国内快、不用备案、不用新账号；它唯一的短板（附件 CDN 的 Content-Type 是 `application/zip`）**恰好不命中 App**。
  代码已经在 `release.yml` 里（`Resolve mirror settings` / `Upload APK to the Gitee mirror` / `Publish the mirror manifest`），当前被 `if: ${{ false }}` 停用。
  - 重启前**必须**改一处：把这几个步骤从"失败即 `exit 1`"改成"失败只 `::warning::`"。v1.5.2 停用它的原因正是"镜像上传偶发失败会把整个发布带红"。
- **产品页浏览器下载 → 保留一个能设 `Content-Type` 的静态站**（Netlify 恢复，或 Cloudflare Pages）。它只影响官网下载按钮，挂了不影响 App 更新。

## 四、割接清单

### 4.1 前置
- [ ] Netlify 已恢复，且成功部署过一次（站点 `Published on` 变成近期日期）
- [ ] 已装机用户至少有一条能走通的路径（确认目标网络里 `api.github.com` 可用）

### 4.2 切到 Gitee 做更新镜像
1. 确认 Secrets 还在：`GITEE_TOKEN`；Variables：`GITEE_OWNER` / `GITEE_REPO`。
2. 确认镜像仓库有分支（Gitee 网页上建一个文件即可产生首个提交）。
3. 去掉 `release.yml` 里 `Resolve mirror settings` 的 `if: ${{ false }}`。
4. 把 `LIVE_MANIFEST_URL` Variable 改成 `https://gitee.com/<owner>/<repo>/raw/<branch>/latest.json`。
   这一个变量同时决定"编译进包的地址"和"发版时校验的地址"，不会各写一份。
5. 打 tag 发版，在 run summary 里确认「线上更新端点」是"已同步"。

### 4.3 验收（真机必做）
- [ ] 新包覆盖安装后能正常启动；手动"检查更新"能查到版本。
- [ ] **屏蔽 `api.github.com`**（路由器 / hosts / 防火墙）后重试，确认仍能从 Gitee 镜像查到新版本
      —— 这才是镜像存在的意义，也是唯一能证明它生效的测法。
- [ ] 产品页下载按钮拿到的是 `.apk` 而不是 `.zip`（如果产品页换了托管）。

### 4.4 回滚
把 `LIVE_MANIFEST_URL` 改回原地址、重新发一版即可。已装机的包各自记着自己的地址，互不影响。

## 五、顺带记两个待办

- 落地页仓库把 13.7 MB 的 APK 提交进了 git，**每发一版都会在历史里留一份**。长期应把二进制挪出仓库（对象存储 / Release 资产），仓库里只留 `latest.json`。
- `latest.json` 的 `apkUrl` 与站点上的 `public/downloads/jicun.apk` 现在指向同一个文件。按第三节拆开角色后，两者可以指向不同托管 —— 这正是"更新不看 Content-Type、浏览器才看"的落点。
