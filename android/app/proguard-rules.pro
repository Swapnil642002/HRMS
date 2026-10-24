# Keep Retrofit models
-keepclassmembers class com.daksha.hrms.data.api.models.** { *; }
-keep class com.daksha.hrms.data.api.models.** { *; }

# Keep OkHttp & Retrofit
-dontwarn okhttp3.**
-dontwarn retrofit2.**
