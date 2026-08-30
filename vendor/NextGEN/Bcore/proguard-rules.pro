# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keep class menu.nextgen.launcher.** {*; }
-keep class menu.nextgen.jnihook.** {*; }
-keep class mirror.** {*; }
-keep class android.** {*; }
-keep class com.android.** {*; }

-keep class menu.nextgen.reflect.** {*; }
-keep @menu.nextgen.reflect.annotation.BClass class * {*;}
-keep @menu.nextgen.reflect.annotation.BClassName class * {*;}
-keep @menu.nextgen.reflect.annotation.BClassNameNotProcess class * {*;}
-keepclasseswithmembernames class * {
    @menu.nextgen.reflect.annotation.BField.* <methods>;
    @menu.nextgen.reflect.annotation.BFieldNotProcess.* <methods>;
    @menu.nextgen.reflect.annotation.BFieldSetNotProcess.* <methods>;
    @menu.nextgen.reflect.annotation.BFieldCheckNotProcess.* <methods>;
    @menu.nextgen.reflect.annotation.BMethod.* <methods>;
    @menu.nextgen.reflect.annotation.BStaticField.* <methods>;
    @menu.nextgen.reflect.annotation.BStaticMethod.* <methods>;
    @menu.nextgen.reflect.annotation.BMethodCheckNotProcess.* <methods>;
    @menu.nextgen.reflect.annotation.BConstructor.* <methods>;
    @menu.nextgen.reflect.annotation.BConstructorNotProcess.* <methods>;
}