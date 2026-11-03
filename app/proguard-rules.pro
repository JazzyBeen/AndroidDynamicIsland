# gomobile invokes these callbacks through JNI; their names are part of the native ABI.
-keep class go.** { *; }
-keep class io.nekohasekai.libbox.** { *; }
-keep class ** implements io.nekohasekai.libbox.PlatformInterface { *; }
-keep class ** implements io.nekohasekai.libbox.StringIterator { *; }
-keep class ** implements io.nekohasekai.libbox.NetworkInterfaceIterator { *; }
