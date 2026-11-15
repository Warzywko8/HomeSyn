# Keep Glance AppWidget ActionCallback subclasses and receiver
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    public <init>();
}
-keep class com.example.widget.DomSyncWidgetReceiver { *; }

# Keep Firestore reflection-deserialized models, no-arg constructors, fields, getters/setters, and annotations
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class com.example.data.model.** {
    <init>(...);
    <fields>;
    <methods>;
}
-keepclassmembers class * {
    @com.google.firebase.firestore.DocumentId <fields>;
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
    @com.google.firebase.firestore.ServerTimestamp <fields>;
    @com.google.firebase.firestore.IgnoreExtraProperties <fields>;
}

# Keep Kotlinx Serialization models & generated companions/serializers (Navigation Compose & Widget cache)
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-dontwarn kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.ShoppingRoute { *; }
-keep,includedescriptorclasses class com.example.ChoresRoute { *; }
-keep,includedescriptorclasses class com.example.ExpensesRoute { *; }
-keep,includedescriptorclasses class com.example.data.widget.** { *; }
