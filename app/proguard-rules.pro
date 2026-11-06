# SQLCipher uses JNI: its classes must not be renamed.
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }
