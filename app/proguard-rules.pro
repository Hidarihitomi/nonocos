# Xposed entry class must match META-INF/xposed/java_init.list.
-keep class com.xixi.nocos.hook.CosaModule { *; }
-keep class com.xixi.nocos.hook.ScreenshotFixModule { *; }
-keep class com.xixi.nocos.hook.LauncherMenuModule { *; }
-keep class com.xixi.nocos.hook.ThemeVipModule { *; }
-keep class com.xixi.nocos.hook.GameStartAnimationModule { *; }
-keep class com.xixi.nocos.hook.KeyboardModule { *; }
-keep class com.xixi.nocos.hook.PermissionManagerModule { *; }
-keep class com.xixi.nocos.hook.MultiAppModule { *; }
-dontwarn io.github.libxposed.**
