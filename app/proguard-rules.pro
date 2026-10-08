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

# greenDAO resolves DAO metadata by name at runtime (DaoConfig).
# MigrationHelper also invokes createTable/dropTable through reflection.
-keep class com.example.gtj_f230_rebound_corrosion.functions.database.greenDao.db.** { *; }

# greenDAO ships optional adapters for SQLCipher and RxJava 1. The app does not
# include or call either adapter, so R8 may safely ignore their absent types.
-dontwarn net.sqlcipher.database.SQLiteOpenHelper
-dontwarn rx.Scheduler
-dontwarn rx.schedulers.Schedulers

# Keep source locations useful when diagnosing release crashes.
-keepattributes SourceFile,LineNumberTable
