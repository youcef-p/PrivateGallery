# Keep Tink and WebRTC entry points; both do reflection-sensitive things.
-keep class com.google.crypto.tink.** { *; }
-keep class org.webrtc.** { *; }
-keepattributes *Annotation*
# Never keep logs of crypto/media classes verbose in release — assert stripped by R8 automatically
# via androidx logging guards; no custom rule needed beyond default optimize.
