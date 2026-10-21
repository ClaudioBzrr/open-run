# Keep Room entities and generated database code reachable after shrinking.
-keepattributes *Annotation*
-keep class com.openrun.app.data.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keepclassmembers class * {
    @androidx.room.* <fields>;
}

# The Garmin FIT SDK is used directly by SessionFitWriter. Keeping it avoids
# aggressive shrinking of message/enum classes used through the encoder API.
-keep class com.garmin.fit.** { *; }
-dontwarn com.garmin.fit.**

# osmdroid loads some classes reflectively.
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
