# 拾光清理 PhotoKeep v0.2.0

安装后直接点击“登录 OneDrive”，通过微软官方页面授权；普通用户不再需要注册应用或填写 Client ID。本版同时更新照片与云朵图标。

## 下载与更新

下载附件 `PhotoKeep-0.2.0.apk`，支持 Android 11 及更新版本。使用与 v0.1.0 相同的 Pigbibi 发布签名，直接覆盖安装即可；不要先卸载，否则会丢失本机对应记录。

旧版已填写的自定义连接会保留。如果要改用内置登录，进入 **设置 → 高级连接设置 → 恢复默认连接**，确认后重新登录并选择 OneDrive 目录。切换会关闭后台检查和自动清理、重建对应关系，不删除照片。

## 更新内容

- 总览和设置均可直接打开微软 OAuth 登录；自定义 Client ID 移至高级连接设置。
- 内置公开的项目注册编号，Android 回调绑定发布包签名。没有客户端密码或开发者服务器。
- 保留旧连接；切换注册或账号时清空旧云端对应关系并关闭定期检查，拒绝过期登录回调。
- 墨绿、暖白和金色的原创矢量图标，适配圆形、圆角和系统主题图标；通知使用单色图形。
- 用户教程与开发者注册教程分开，文档面向通用 Android 设备。

## 验证与边界

已执行自动化测试、发布构建、Android lint 和 APK 签名核验；详细结果见 [v0.2.0 验证记录](https://github.com/Pigbibi/OneDriveDeletionHelper/blob/main/docs/VALIDATION-v0.2.0.md)。微软应用注册、发布包 Android 回调和 `Files.ReadWrite` 委托权限已配置。

本版仍为预发布版。真实账号完成授权、令牌刷新及真实 OneDrive 回收站操作尚未完成端到端验证；工作/学校账号可能需要组织管理员批准。请先使用测试目录，确认实际行为后再考虑自动清理。

PhotoKeep 继续只负责清理核对，OneDrive 官方应用负责备份。Google Photos 只在云端删除的照片、无法关联的历史多余照片以及直接消失但无法确认原因的本地文件，不会自动清理。参阅 [中文教程](https://github.com/Pigbibi/OneDriveDeletionHelper/blob/main/docs/SETUP.zh-CN.md)。

MIT License，Copyright (c) 2026 Pigbibi。附件含 APK、SHA-256 校验文件、公开签名和应用注册信息。
