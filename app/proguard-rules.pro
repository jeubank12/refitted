# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

-dontwarn com.amazonaws.mobileconnectors.appsync.AppSyncQueryCall
-dontwarn com.amazonaws.mobileconnectors.appsync.AppSyncSubscriptionCall$Callback
-dontwarn com.amazonaws.mobileconnectors.appsync.AppSyncSubscriptionCall
-dontwarn com.amazonaws.mobileconnectors.appsync.fetcher.AppSyncResponseFetchers
-dontwarn com.apollographql.apollo.GraphQLCall$Callback
-dontwarn com.apollographql.apollo.api.Operation$Variables
-dontwarn com.apollographql.apollo.api.Query
-dontwarn com.apollographql.apollo.api.Response
-dontwarn com.apollographql.apollo.api.Subscription
-dontwarn com.apollographql.apollo.fetcher.ResponseFetcher
