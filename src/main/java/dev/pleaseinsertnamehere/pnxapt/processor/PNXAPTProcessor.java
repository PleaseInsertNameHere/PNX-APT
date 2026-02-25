package dev.pleaseinsertnamehere.pnxapt.processor;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import dev.pleaseinsertnamehere.pnxapt.annotations.AutoListener;
import dev.pleaseinsertnamehere.pnxapt.annotations.Scheduler;
import dev.pleaseinsertnamehere.pnxapt.annotations.pluginmeta.LoadOrder;
import dev.pleaseinsertnamehere.pnxapt.annotations.pluginmeta.PluginMeta;
import dev.pleaseinsertnamehere.pnxapt.utils.Constants;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Set;

@AutoService(Processor.class)
@SupportedAnnotationTypes({
        "dev.pleaseinsertnamehere.pnxapt.annotations.AutoListener",
        "dev.pleaseinsertnamehere.pnxapt.annotations.pluginmeta.PluginMeta",
        "dev.pleaseinsertnamehere.pnxapt.annotations.Scheduler"
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class PNXAPTProcessor extends AbstractProcessor {

    private static class SchedulerMethod {
        TypeElement classElement;
        Element methodElement;
        Scheduler schedulerAnnotation;

        SchedulerMethod(TypeElement classElement, Element methodElement, Scheduler schedulerAnnotation) {
            this.classElement = classElement;
            this.methodElement = methodElement;
            this.schedulerAnnotation = schedulerAnnotation;
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        processPluginMeta(roundEnv);

        ArrayList<TypeElement> listeners = collectListeners(roundEnv);
        ArrayList<SchedulerMethod> schedulerMethods = collectSchedulerMethods(roundEnv);

        generatePNXAPT(listeners, schedulerMethods);
        return true;
    }

    /**
     * Collects all classes annotated with @AutoListener and checks if they implement cn.nukkit.event.Listener.
     *
     * @param roundEnv The round environment to search for annotated elements
     * @return A list of classes annotated with @AutoListener that implement cn.nukkit.event.Listener
     */
    private ArrayList<TypeElement> collectListeners(RoundEnvironment roundEnv) {
        ArrayList<TypeElement> listeners = new ArrayList<>();

        for (Element element : roundEnv.getElementsAnnotatedWith(AutoListener.class)) {
            if (element.getKind() != ElementKind.CLASS) continue;

            TypeElement classElement = (TypeElement) element;
            if (classElement.getInterfaces().stream()
                    .noneMatch(i -> i.toString().equals("cn.nukkit.event.Listener"))) {
                this.processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "Classes annotated with @AutoListener must implement cn.nukkit.event.Listener",
                        element
                );
                continue;
            }

            listeners.add(classElement);
        }

        return listeners;
    }

    /**
     * Collects all methods and classes annotated with @Scheduler, ensuring that method annotations are on public static methods
     * and that annotated classes implement Runnable.
     * @param roundEnv The round environment to search for annotated elements
     * @return A list of SchedulerMethod objects representing the annotated methods and classes
     */
    private ArrayList<SchedulerMethod> collectSchedulerMethods(RoundEnvironment roundEnv) {
        ArrayList<SchedulerMethod> schedulerMethods = new ArrayList<>();

        for (Element element : roundEnv.getElementsAnnotatedWith(Scheduler.class)) {
            Scheduler schedulerAnnotation = element.getAnnotation(Scheduler.class);

            if (element.getKind() == ElementKind.METHOD) {
                ExecutableElement exec = (ExecutableElement) element;
                TypeElement classElement = (TypeElement) element.getEnclosingElement();

                Set<Modifier> mods = exec.getModifiers();
                if (!mods.contains(Modifier.PUBLIC) || !mods.contains(Modifier.STATIC)) {
                    this.processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.ERROR,
                            "Methods annotated with @Scheduler must be public and static",
                            element
                    );
                    continue;
                }

                schedulerMethods.add(new SchedulerMethod(classElement, element, schedulerAnnotation));

            } else if (element.getKind() == ElementKind.CLASS) {
                TypeElement classElement = (TypeElement) element;
                if (classElement.getInterfaces().stream()
                        .noneMatch(i -> i.toString().equals("java.lang.Runnable"))) {
                    this.processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.ERROR,
                            "Classes annotated with @Scheduler must implement java.lang.Runnable",
                            element
                    );
                    continue;
                }
                schedulerMethods.add(new SchedulerMethod(classElement, null, schedulerAnnotation));
            }
        }

        return schedulerMethods;
    }

    /**
     * Processes the @PluginMeta annotation, ensuring it's applied correctly and generating the plugin.yml file.
     * @param roundEnv The round environment to search for annotated elements
     */
    private void processPluginMeta(RoundEnvironment roundEnv) {
        Set<? extends Element> annotated = roundEnv.getElementsAnnotatedWith(PluginMeta.class);

        if (annotated.isEmpty()) return;

        if (annotated.size() > 1) {
            for (Element element : annotated) {
                this.processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "@PluginMeta can only be applied to one class",
                        element
                );
            }
            return;
        }

        Element element = annotated.iterator().next();
        if (element.getKind() != ElementKind.CLASS) {
            this.processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@PluginMeta can only be applied to classes",
                    element
            );
            return;
        }

        TypeElement classElement = (TypeElement) element;
        if (!classElement.getSuperclass().toString().equals("cn.nukkit.plugin.PluginBase")) {
            this.processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "The class annotated with @PluginMeta must extend cn.nukkit.plugin.PluginBase",
                    element
            );
            return;
        }

        PluginMeta meta = classElement.getAnnotation(PluginMeta.class);
        String mainClass = classElement.getQualifiedName().toString();
        generatePluginYml(meta, mainClass);
    }

    private void generatePluginYml(PluginMeta meta, String mainClass) {
        try {
            FileObject file = processingEnv.getFiler()
                    .createResource(StandardLocation.CLASS_OUTPUT, "", "plugin.yml");

            try (Writer writer = file.openWriter()) {
                writer.write(buildYaml(meta, mainClass));
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage());
        }
    }

    private String buildYaml(PluginMeta meta, String mainClass) {
        StringBuilder sb = new StringBuilder();

        sb.append("name: ").append(meta.name()).append("\n");
        sb.append("version: ").append(meta.version()).append("\n");
        sb.append("main: ").append(mainClass).append("\n");
        sb.append("api: ").append(formatList(meta.api())).append("\n");

        if (!meta.description().isEmpty())
            sb.append("description: ").append(meta.description()).append("\n");

        if (meta.authors().length > 0)
            sb.append("authors: ").append(formatList(meta.authors())).append("\n");

        if (!meta.website().isEmpty())
            sb.append("website: ").append(meta.website()).append("\n");

        if (!meta.prefix().isEmpty())
            sb.append("prefix: ").append(meta.prefix()).append("\n");

        if (meta.depend().length > 0)
            sb.append("depend: ").append(formatList(meta.depend())).append("\n");

        if (meta.softDepend().length > 0)
            sb.append("softdepend: ").append(formatList(meta.softDepend())).append("\n");

        if (meta.loadBefore().length > 0)
            sb.append("loadbefore: ").append(formatList(meta.loadBefore())).append("\n");

        if (meta.order() != LoadOrder.POSTWORLD)
            sb.append("load: ").append(meta.order().name()).append("\n");

        if (meta.features().length > 0)
            sb.append("features: ").append(formatList(meta.features())).append("\n");

        return sb.toString();
    }

    private String formatList(String[] values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            sb.append(values[i]);
            if (i < values.length - 1) sb.append(", ");
        }
        return sb.append("]").toString();
    }

    // Code generation
    private void generatePNXAPT(ArrayList<TypeElement> listeners, ArrayList<SchedulerMethod> schedulerMethods) {
        if (listeners.isEmpty() && schedulerMethods.isEmpty()) return;

        MethodSpec.Builder method = MethodSpec.methodBuilder("init")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(ClassName.get("cn.nukkit.plugin", "Plugin"), "plugin");

        if (!listeners.isEmpty()) {
            method.addStatement("$T pluginManager = plugin.getServer().getPluginManager()",
                    ClassName.get("cn.nukkit.plugin", "PluginManager"));

            for (TypeElement listener : listeners) {
                method.addStatement("pluginManager.registerEvents(new $T(), plugin)",
                        ClassName.get(listener));
            }
        }

        if (!schedulerMethods.isEmpty()) {
            method.addStatement("$T scheduler = plugin.getServer().getScheduler()",
                    ClassName.get("cn.nukkit.scheduler", "ServerScheduler"));

            for (SchedulerMethod schedulerMethod : schedulerMethods) {
                if (schedulerMethod.methodElement == null) { // Class
                    method.addStatement("scheduler.scheduleDelayedRepeatingTask(plugin, new $T(), $L, $L, $L)",
                            ClassName.get(schedulerMethod.classElement),
                            schedulerMethod.schedulerAnnotation.delay(),
                            schedulerMethod.schedulerAnnotation.period(),
                            schedulerMethod.schedulerAnnotation.async());
                } else { // Method
                    String classPath = schedulerMethod.classElement.getQualifiedName() + "." + schedulerMethod.methodElement.getSimpleName();
                    method.addStatement("scheduler.scheduleDelayedRepeatingTask(plugin, new $T() { @$T public void run() { $L(); } }, $L, $L, $L)",
                            ClassName.get("java.lang", "Runnable"),
                            ClassName.get("java.lang", "Override"),
                            classPath,
                            schedulerMethod.schedulerAnnotation.delay(),
                            schedulerMethod.schedulerAnnotation.period(),
                            schedulerMethod.schedulerAnnotation.async());
                }
            }
        }

        TypeSpec pnxapt = TypeSpec.classBuilder("PNXAPT")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addJavadoc("AUTO-GENERATED by pnx-apt - DO NOT EDIT\n")
                .addMethod(method.build())
                .build();

        try {
            JavaFile.builder(Constants.GENERATED_PACKAGE_LOCATION, pnxapt)
                    .build()
                    .writeTo(this.processingEnv.getFiler());
        } catch (IOException e) {
            this.processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "Failed to generate PNXAPT class: " + e.getMessage()
            );
        }
    }
}
