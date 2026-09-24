package v.akfz.db.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Compiles and packs this class only for the listed loaders. Requires dbuild plugin's dontCompile(). */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface OnlyLoader {
    Loader[] value();

    enum Loader { FABRIC, QUILT, FORGE, NEOFORGE }
}