# HICHRAWI LIVE - R8 rules

# Keep LibVLC JNI/native bridge
-keep class org.videolan.libvlc.** { *; }
-keep class org.videolan.libvlc.interfaces.** { *; }

# Keep Firebase
-keep class com.google.firebase.** { *; }

# Keep model/API classes used by JSON reflection
-keep class com.hichrawi.tv.** { *; }

# Keep Parcelable/Serializable implementations
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
}
