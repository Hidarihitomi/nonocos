# nocos

面向 ColorOS 17 的系统增强模块合集，基于 [libxposed API 102](https://github.com/libxposed/api)，一个 APK 内含 7 个二级页面，所有开关默认关闭。

- 包名：`com.xixi.nocos`
- 当前版本：`1.9.6`（`versionCode 125`）
- 运行环境：ColorOS 17 / Android 17，`minSdk 27`
- 依赖：支持 libxposed API 102 的框架（如 LSPosed）

## 模块

| 模块 | 作用应用 | 二级界面功能 |
| --- | --- | --- |
| 截屏 | `com.oplus.screenshot` | 区域截屏增强：三指区域截屏松手后直接进入框选。 |
| 桌面 | `com.android.launcher` | 二级菜单平铺：长按图标时，把「更多功能」里的项目直接展开到一级菜单。<br>隐藏清理按键：最近任务底部的「清理」按键不再显示，内存信息保留。 |
| 主题商店 | `com.heytap.themestore` | 解锁部分 VIP 功能：解锁客户端部分会员功能并保留试用资源。<br>去广告：屏蔽开屏广告、营销弹窗与已识别的广告卡片。<br>深色模式沿用系统主题：让作用域内的应用在深色模式下继续使用已套用主题的图标等资源。 |
| 游戏助手 | `com.oplus.games`、`com.oplus.cosa` | 屏蔽风驰启动动画：启动游戏时不再显示「一加风驰游戏内核」启动动画。<br>游戏云控伪装：伪装系统机型代码，让云控按指定机型下发配置。 |
| 小布输入法 | `com.oplus.keyboard` | 解锁更多应用的键盘材质：允许更多应用使用键盘原生模糊材质。<br>键盘莫奈配色：采用系统壁纸取色，为键盘、按键和工具栏统一配色。 |
| 权限管理 | `com.oplus.securitypermission`、`com.android.settings`、`com.android.permissioncontroller`、`system` | 旧版应用启动弹窗：使用系统旧版的「允许应用启动」确认弹窗。<br>始终允许应用启动：把启动弹窗里的「允许 30 天」替换为「始终允许」。<br>自动解除受限设置：打开应用的权限管理页时，自动清除该应用的「受限设置」限制。<br>跳过恶意应用拦截：跳过系统对「恶意应用」的启动拦截，仍保留正常的启动确认与权限检查。修改后需重启系统。<br>国际版权限页面：权限管理使用国际版的权限分类与应用权限详情页面。修改后需重启设置与权限控制器。 |
| 应用分身 | `com.oplus.multiapp`、`system` | 解除分身应用限制：允许为任意已安装应用创建分身，不再受系统白名单限制。修改后需重启系统。<br>分身数量上限：把每个应用可分身的数量上限提高到 10 个。 |

## 安装与使用

1. 安装 APK。
2. 在 LSPosed 中启用模块，并勾选需要生效的作用域（APK 已通过 `scope.list` 声明）。
3. 打开模块 App，按需开启各功能并保存。所有开关默认关闭。
4. 重新打开目标应用生效。权限管理、应用分身涉及 `system_server`，修改后需要重启手机。

## 构建

要求 JDK 21、Android SDK Platform 37、Build-Tools 36.0.0。

```bash
./gradlew :app:assembleRelease
```

产物为 `app/build/outputs/apk/release/app-release-unsigned.apk`，需要自行对齐和签名：

```bash
zipalign -f -p 4 app-release-unsigned.apk aligned.apk
apksigner sign --ks your.jks --out nocos-1.9.6.apk aligned.apk
```

仓库自带 GitHub Actions（`.github/workflows/build.yml`），推送 `v*` 标签时会构建、签名并发布 APK。

## 项目结构

```
app/src/main/java/com/xixi/nocos/
├── common/   各模块配置常量与目标类 / 方法名
├── data/     RemotePreferences 配置读写
├── hook/     8 个 Xposed 模块入口
└── ui/       Compose 配置界面
```

## 兼容性

Hook 依赖目标应用的类名和方法名，系统应用升级后可能失效。代码中标注了各模块实测的应用版本（截屏 17.7.10 / 17.9.0、桌面 17.3.12、主题商店 17.16.0、游戏助手 11.0.4、小布输入法 1.9.25.17），部分入口保留了旧版兜底逻辑。

## 许可证

[MIT License](LICENSE)
