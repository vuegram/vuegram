# VueGram's release build doesn't shrink or obfuscate (isMinifyEnabled = false in build.gradle.kts).
# If that changes, TDLib's Java bindings must keep their names: libtdjni.so finds them by class name.
-keep class org.drinkless.tdlib.** { *; }
