# Native-backed libraries resolve Java classes and methods from JNI by name.
-keep class com.lambdapioneer.argon2kt.** { *; }
-keep class net.zetetic.database.** { *; }
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.auto.value.**
-dontwarn javax.lang.model.**

# Keep line numbers for crash analysis without exposing source file names.
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
