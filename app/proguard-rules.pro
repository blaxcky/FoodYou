-keep class com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType

# LiteRT-LM ships no consumer rules, and its native library calls back into these classes via JNI.
-keep class com.google.ai.edge.litertlm.** { *; }
