# Room generates implementations that are looked up by name.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class app.netoverdose.data.**_Impl { *; }
-dontwarn androidx.room.paging.**

# TypeConverters use enum name()/valueOf: keep enum member names intact.
-keepclassmembers enum app.netoverdose.domain.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    <fields>;
}

# WorkManager instantiates workers reflectively by class name.
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class app.netoverdose.notify.ReminderWorker { *; }

# Export codec relies on the platform org.json, nothing to keep there.
-dontwarn org.json.**
