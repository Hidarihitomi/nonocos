# Xposed entry class must match META-INF/xposed/java_init.list.
-keep class com.xixi.nocos.hook.CosaModule { *; }
-keep class com.xixi.nocos.hook.ScreenshotFixModule { *; }
-keep class com.xixi.nocos.hook.LauncherMenuModule { *; }
-keep class com.xixi.nocos.hook.ThemeVipModule { *; }
-dontwarn io.github.libxposed.**
