package net.neoforged.fml.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Compile-time stand-in for NeoForge's {@code @Mod} (same name and {@code value}); never packaged. NeoForge finds the
 * annotation by its name in the class file, so the bridge needs no NeoForge jar to build.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Mod {
    String value();
}
