# Charge Master R8 rules.
# Room, Hilt, WorkManager and Glance ship their own consumer rules.
# Keep enum names because they are persisted in Room/DataStore by name.
-keepclassmembers enum com.hikmahlab.chargemaster.** { *; }
