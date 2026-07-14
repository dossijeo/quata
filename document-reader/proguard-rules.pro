# Consumer rules for the embedded Qüata document reader.
# The legacy document engine uses class-name lookup, reflective constructors and
# JNI entry points throughout its PDF/Office/EMF parsers. Keep this isolated
# module stable while R8 optimizes and shrinks the rest of the application.
-keep class com.quata.documentreader.** { *; }
