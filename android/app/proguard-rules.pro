# R8 rules for SecureCam.
# CameraX and Compose ship consumer rules; nothing custom is required today.
# Keep crash reports readable by retaining source metadata.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
