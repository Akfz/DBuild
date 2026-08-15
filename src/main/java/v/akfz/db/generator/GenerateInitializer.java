package v.akfz.db.generator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface GenerateInitializer {

    LoaderType loader();

    Class<? extends InitializerClass> mainClass() default NotAClass.class;

    String modId();

    boolean isClient() default false;

    String packageStr() default ""; // null = generate

    String addClassNameForge() default "_forge"; // generic names like - <classname> + addClassName (its important for fabric)

    String addClassNameFabric() default "_fabric";
}
