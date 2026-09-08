# 微软应用注册：仅供维护者与自行编译者

安装项目发布 APK 的普通用户只需点击“登录 OneDrive”。本页是开发者配置，不是用户安装前置条件。

## 项目发布包的公开配置

| 配置 | 值 |
|---|---|
| 显示名称 | PhotoKeep 拾光清理 |
| Application (client) ID | `c3030afc-9d47-49be-a7e5-610566f6736c` |
| Android 包名 | `cn.lisiyi.photokeep` |
| 发布签名哈希 | `jii5ziPMnwaprmEmaYnwKgxYX4o=` |
| Redirect URI | `msauth://cn.lisiyi.photokeep/jii5ziPMnwaprmEmaYnwKgxYX4o%3D` |
| 账号类型 | 任何组织目录和个人 Microsoft 账号 |
| Graph 委托权限 | `Files.ReadWrite` |

Client ID 与签名哈希是公开信息，不是密码。APK 使用微软 MSAL 的浏览器 OAuth 登录，每位用户独立授权；不使用 Client Secret，不需要项目服务器。不要提交私钥、令牌、账号缓存或客户端密码。

## 注册自己的应用

1. 打开 [Microsoft Entra 管理中心](https://entra.microsoft.com/)，使用有应用注册权限的账号进入 **应用注册 → 新注册**。
2. 填写应用名称，支持的账号类型选择 **任何组织目录中的账户和个人 Microsoft 账户**，与当前代码的 `common` 登录入口一致。
3. 记录 **Application (client) ID**，不要误用 Object ID 或 Tenant ID。
4. 进入 **身份验证 → 添加平台/添加重定向 URI → Android**，填写自己的包名和 APK 签名哈希，保存回调地址。应用的 **高级连接设置 → 自定义 Client ID** 页面可以复制包名和签名哈希。
5. 在 **API 权限 → 添加权限 → Microsoft Graph → 委托权限** 中声明 `Files.ReadWrite`。不要添加应用程序权限，不需要创建客户端密码，也不要为整个组织盲目授予管理员同意。
6. 在品牌属性中填写项目主页与隐私说明。工作/学校账号的用户同意取决于其组织策略；未验证发布者的多租户应用可能要求管理员批准。
7. 修改 `config/microsoft.properties` 的 `clientId`，重新构建；也可在自己的安装中通过高级设置填入公开 ID。

只有注册过的包名与签名组合能使用对应回调。自行编译的调试 APK 与项目发布包签名不同，应使用自己的注册；不要申请将任意调试签名加入项目生产注册。

## 构建和升级行为

- 构建时读取 `config/microsoft.properties`，可用 Gradle 属性 `-PphotokeepClientId=公开应用ID` 覆盖。该属性只能填公开 ID。
- 发布构建拒绝空值、格式错误和全零 ID。此检查仅确认配置存在，不能代替微软后台配置与真实授权验证。
- 离线开发可传 `-PphotokeepClientId=` 构建调试包；登录入口会提示安装包未开放登录，不会要求普通用户注册应用。
- 新安装和旧版未配置连接的安装使用内置 ID。旧版已保存的 ID 保留，即使新的发布包内置 ID 不同，也不会悄悄切换账号授权来源。
- 主动切换连接会要求确认、关闭后台检查和自动清理，并清空原账号云端关联。不会删除云端照片。

参考：[微软 Android 登录示例](https://learn.microsoft.com/en-us/entra/identity-platform/quickstart-mobile-app-sign-in)、[公共客户端与 Client ID](https://learn.microsoft.com/en-us/entra/identity-platform/msal-client-applications)、[MSAL Android 配置](https://learn.microsoft.com/en-us/entra/msal/android/msal-configuration)。
