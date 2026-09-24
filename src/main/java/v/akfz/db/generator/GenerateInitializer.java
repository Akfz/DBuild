package v.akfz.db.generator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generates loader entry points for Forge/Fabric/NeoForge.
 * <p>
 * <b>Do it exactly like this</b>, or it won't initialize:
 * <pre>{@code
 * @GenerateInitializer(modId = "aslib")
 * public class AsLib {
 *     public void init() { ... }
 * }
 * }</pre>
 * No interface needed, no superclass — just a {@code public void init()}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface GenerateInitializer {

    /** Target modloader type (Forge, Fabric, or Both) */
    LoaderType loader() default LoaderType.Both;

    /** Class to instantiate (defaults to annotated class) */
    Class<?> mainClass() default NotAClass.class;

    /** Target Mod ID (required for Forge/NeoForge) */
    String modId();

    /**
     * Replaces default loader by LoaderGuide method (read javadoc inside)
     */
    Class<?>[] guides() default {};

    /** Marks this as a client-side only initializer (ClientModInitializer for Fabric, ClientSetup for Forge) */
    boolean isClient() default false;

    /** Package for generated classes (empty string = same package as annotated class) */
    String packageStr() default "";

    /** Suffix added to the generated Forge class name */
    String addClassNameForge() default "_forge";

    /** Suffix added to the generated Fabric class name */
    String addClassNameFabric() default "_fabric";
}