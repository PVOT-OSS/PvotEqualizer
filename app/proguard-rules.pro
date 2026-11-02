# Add project specific ProGuard rules here.
# For more details, see https://developer.android.com/build/shrink-code

# The audio engine is reached only through the AudioEngine interface and the
# service/receiver components named in the manifest; keep the DSP value types
# that kotlinx.serialization will read in a later phase.
-keep,allowobfuscation,allowshrinking class org.prauga.equalizer.domain.**
