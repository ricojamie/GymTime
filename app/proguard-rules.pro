# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve source file & line numbers for deobfuscated stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve SavedStateHandle Serializable payloads across process death & R8.
-keepnames class com.example.gymtime.ui.routine.RoutineDayDraft
-keepclassmembers class com.example.gymtime.ui.routine.RoutineDayDraft {
    *;
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Preserve enum constant names persisted to Room/CSV or resolved via Enum.valueOf().
-keepclassmembers enum com.example.gymtime.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public static final <fields>;
}

# Preserve WorkManager worker class name persisted in WorkSpec database.
-keep class com.example.gymtime.notifications.MonthlyReportWorker { *; }
