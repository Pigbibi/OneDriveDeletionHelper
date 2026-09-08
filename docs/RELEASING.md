# 发布与签名

## 构建发布包

先按[开发者注册教程](MICROSOFT-APP.md)完成项目微软应用注册，确认 `config/microsoft.properties` 中的公开 Client ID 及微软后台的发布签名回调。发布构建会拒绝未配置的 Client ID。

配置 Java 17 与 Android SDK 后，项目的首次发布者执行：

```sh
python3 scripts/build-release.py --init-key
```

脚本将签名密钥保存在仓库外的 `~/.local/share/photokeep/signing/`，目录权限 700、文件权限 600。密码通过临时进程环境交给构建工具，不放在命令参数或仓库里。这个目录包含敏感签名材料，必须单独安全备份，不能提交、分享或作为 GitHub artifact 上传。

后续更新使用同一个密钥：

```sh
python3 scripts/build-release.py
```

可用 `PHOTOKEEP_SIGNING_DIR` 指定另一个仓库外的私有目录。没有旧密钥时不要为已经发布的应用重建密钥，否则用户不能覆盖升级。APK 的微软 Android 签名哈希也会改变，需要修改微软应用注册。

脚本会执行测试、release lint 和 release 构建，输出 `dist/PhotoKeep-0.2.0.apk` 及 `dist/SHA256SUMS.txt`。发布前用 SDK 的 `apksigner verify --verbose --print-certs` 验证签名；证书指纹是公开信息，可以随发布说明公布。版本号从 `app/build.gradle.kts` 读取，输出文件名自动跟随。

## 发布规则

- `v0.2.0` 内置项目的公开 Microsoft client ID，使用与 v0.1.0 相同的发布密钥，允许覆盖升级。
- 保留 v0.1.0 的 tag 和附件，不覆盖旧 APK；v0.1.0 未内置 Client ID，新版本通过单独 tag 发布。
- 只上传 APK、校验文件和用户说明，不上传 keystore、密码、本地记录、照片或登录缓存。
- GitHub Actions 负责测试、lint 和调试构建。CI 调试 APK 的签名与正式发布包不同，仅供开发验证。
- 发布说明准确列出已完成的编译、测试和界面检查；真实账号授权与手机删除端到端验证不能由这些结果替代。
- 真实删除测试只对本人明确选定的测试照片进行，优先通过预览确认。
