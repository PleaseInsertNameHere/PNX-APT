package dev.pleaseinsertnamehere.pnxapt.annotations.pluginmeta;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface PluginMeta {
    String name();
    String version();
    String[] api();

    String description() default "";
    String[] authors() default {};
    String website() default "";
    String prefix() default "";

    String[] depend() default {};
    String[] softDepend() default {};
    String[] loadBefore() default {};

    LoadOrder order() default LoadOrder.POSTWORLD;

    String[] features() default {};
}
