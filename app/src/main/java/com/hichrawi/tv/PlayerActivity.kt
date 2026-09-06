<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#000000">

    <androidx.media3.ui.PlayerView
        android:id="@+id/playerView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:keepScreenOn="true" />

    <ImageView
        android:id="@+id/channelLogo"
        android:layout_width="115dp"
        android:layout_height="75dp"
        android:layout_gravity="bottom|end"
        android:layout_marginEnd="18dp"
        android:layout_marginBottom="18dp"
        android:scaleType="centerInside"
        android:visibility="gone"
        android:contentDescription="شعار القناة" />

    <TextView
        android:id="@+id/playerMessage"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|center_horizontal"
        android:layout_marginBottom="25dp"
        android:textColor="#FFFFFF"
        android:textSize="16sp"
        android:background="#99000000"
        android:padding="12dp" />
</FrameLayout>
