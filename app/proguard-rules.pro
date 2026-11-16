# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# --- General & Stack Traces ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# --- Retrofit & OkHttp ---
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn okio.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# --- Gson & Remote Models (DTO) ---
-keepattributes EnclosingMethod
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class pro.eng.yui.android.osmjppostalmap.data.remote.** { *; }
-keep class pro.eng.yui.android.osmjppostalmap.domain.model.** { *; }

# --- OSMDroid ---
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# --- Signpost (OAuth) ---
-keep class oauth.signpost.** { *; }
-dontwarn oauth.signpost.**

# --- jppostalcore ---
-keep class pro.eng.yui.oss.osm.lib.jppostalcore.** { *; }
-dontwarn pro.eng.yui.oss.osm.lib.jppostalcore.**
-keep class info.matsudamper.jppostalcore.** { *; }
-dontwarn info.matsudamper.jppostalcore.**

# --- Android Standard Components & Custom Views ---
-keepclassmembers class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
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