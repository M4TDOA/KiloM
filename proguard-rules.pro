# The WebView calls these annotated methods by their names.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses,EnclosingMethod
-keepclassmembers class ca.m4tdoa.kilom.MainActivity$Bridge {
    @android.webkit.JavascriptInterface public <methods>;
}
