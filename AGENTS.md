# AGENTS.md

## 项目是什么

PhotoKeep，一个直连 Microsoft Graph 的开源安卓应用。照片和视频的原件备份仍由官方 OneDrive 应用负责，PhotoKeep 负责在本地整理和清理。

## 技术栈

Java / Android，Gradle（`build.gradle.kts`）。

## 目录

- `app/` — 应用模块。`MainActivity.java` 是主界面。
- `docs/` — `design.md` 是设计，`privacy.md` 是隐私说明，`microsoft-app.md` 是应用注册指引，`releasing.md` 是发版流程，`setup.zh-CN.md` 是中文安装说明。
- `scripts/` — 构建辅助。
- `config/` — 配置。

## 本地验证

按改动选择相关离线检查，命令以仓库当前 CI 为准。先区分代码问题与环境条件，不为通过检查降低保护。

**单元测试、Lint、编译**

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## 命名与文档

- 新增文档文件名小写、`-` 连接，中文版后缀 `.zh-CN.md`；`README.md`、`AGENTS.md` 等工具约定文件保留固定名称。源码沿用现有语言命名。
- 不提交审计报告、复盘、交接单、进度快照、升级计划。要追溯看 commit 历史。
- 改文件名时同一次提交改完所有引用。
- 提交前跑 `python scripts/check_hygiene.py`。

## 不要做

- **不要扩大删除范围。** 这个应用会真删用户照片，任何涉及筛选条件的改动都要先确认边界，宁可少删。
- 不要在没有回收站兜底的情况下删文件。
- 不要在日志、崩溃上报或 issue 模板里输出文件路径、账号邮箱或 Graph token。
- 不要改 Microsoft 客户端 ID 的校验逻辑来绕过发布检查。

## 提交与 PR

Conventional Commits，标题中文，不超过 50 字。类型只用 `feat` `fix` `docs` `refactor` `test` `chore` `perf` `ci`。

分支名 `<type>/<主题>`，例如 `docs/normalize-filenames`。不直接推默认分支，不 force push 已 review 的分支。

提交、推送、合并及生产动作按用户已明确授权的范围执行；同范围已有授权不重复确认。

PR 描述写改了什么、为什么改、怎么验证。
