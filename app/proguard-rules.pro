# Wolf Wheels · R8 rules
-dontobfuscate
# The app's own code stays whole (it is small); R8 only trims unused library code.
-keep class com.wolfwheels.app.** { *; }
# WorkManager builds the worker by name
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
# Optional dependencies of libraries that are not in the app
-dontwarn com.google.re2j.**
-dontwarn org.jspecify.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
