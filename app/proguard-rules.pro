# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep CallScreeningService
-keep class com.callscreen.app.screening.** { *; }
