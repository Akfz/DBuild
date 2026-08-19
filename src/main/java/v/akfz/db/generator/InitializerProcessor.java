package v.akfz.db.generator;

import java.io.IOException;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

/**
 * Safe Initializer annotation processor using standard compiler Filer API.
 */
@SupportedAnnotationTypes("v.akfz.db.generator.GenerateInitializer")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class InitializerProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Filer filer = processingEnv.getFiler();
        String currentTarget = processingEnv.getOptions().get("modLoaderTarget");

        if (currentTarget == null || "common".equalsIgnoreCase(currentTarget) || "none".equalsIgnoreCase(currentTarget)) {
            return true;
        }

        boolean isFabric = "fabric".equalsIgnoreCase(currentTarget);
        boolean isForge = "forge".equalsIgnoreCase(currentTarget);
        boolean isNeoForge = "neoforge".equalsIgnoreCase(currentTarget);

        for (Element element : roundEnv.getElementsAnnotatedWith(GenerateInitializer.class)) {
            if (!(element instanceof TypeElement typeElement)) continue;

            GenerateInitializer annotation = typeElement.getAnnotation(GenerateInitializer.class);

            LoaderType loader = annotation.loader();
            String modId = annotation.modId();
            boolean isClient = annotation.isClient();
            String packageId = annotation.packageStr();

            TypeName mainClass = getMainClassTypeName(annotation, typeElement);

            String endPackage = (packageId.isEmpty() || "null".equalsIgnoreCase(packageId))
                    ? processingEnv.getElementUtils().getPackageOf(typeElement).getQualifiedName().toString()
                    : packageId;

            boolean loaderAllowsFabric = loader == null || loader == LoaderType.FabricLike || loader == LoaderType.Both;
            boolean loaderAllowsForge = loader == null || loader == LoaderType.ForgeLike || loader == LoaderType.Both;

            if (isFabric && loaderAllowsFabric) {
                generateFabricInitializer(filer, mainClass, isClient, endPackage, annotation.addClassNameFabric());
            }

            if ((isForge || isNeoForge) && loaderAllowsForge) {
                generateForgeInitializer(filer, mainClass, modId, isClient, endPackage, annotation.addClassNameForge(), isNeoForge);
            }
        }
        return true;
    }

    private TypeName getMainClassTypeName(GenerateInitializer annotation, TypeElement annotatedElement) {
        try {
            Class<?> clazz = annotation.mainClass();
            return NotAClass.class.equals(clazz) ? ClassName.get(annotatedElement) : ClassName.get(clazz);
        } catch (MirroredTypeException mte) {
            TypeMirror typeMirror = mte.getTypeMirror();
            return typeMirror.toString().equals(NotAClass.class.getName())
                    ? ClassName.get(annotatedElement)
                    : TypeName.get(typeMirror);
        }
    }

    private void generateForgeInitializer(Filer filer, TypeName mainClass, String modId, boolean isClient,
                                          String packageId, String addClassName, boolean isNeoForge) {

        String generatedClassName = getSimpleName(mainClass) + addClassName;
        String basePkg = isNeoForge ? "net.neoforged" : "net.minecraftforge";

        ClassName modAnnotation = ClassName.get(basePkg + ".fml.common", "Mod");
        ClassName contextClass = ClassName.get(basePkg + ".fml.javafmlmod", "FMLJavaModLoadingContext");
        ClassName setupEventClass = ClassName.get(basePkg + ".fml.event.lifecycle",
                isClient ? "FMLClientSetupEvent" : "FMLCommonSetupEvent");

        FieldSpec mainInstanceField = FieldSpec.builder(mainClass, "MAININSTANCE", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer("new $T()", mainClass)
                .build();

        MethodSpec getInstanceMethod = MethodSpec.methodBuilder("getInstance")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(mainClass)
                .addStatement("return MAININSTANCE")
                .build();

        MethodSpec setupMethod = MethodSpec.methodBuilder("onSetup")
                .addModifiers(Modifier.PRIVATE)
                .addParameter(setupEventClass, "event")
                .addStatement("MAININSTANCE.init()")
                .build();

        MethodSpec constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addStatement("$T.get().getModEventBus().addListener(this::onSetup)", contextClass)
                .build();

        TypeSpec generatedClass = TypeSpec.classBuilder(generatedClassName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(modAnnotation).addMember("value", "$S", modId).build())
                .addField(mainInstanceField)
                .addMethod(getInstanceMethod)
                .addMethod(constructor)
                .addMethod(setupMethod)
                .build();

        writeClass(filer, packageId, generatedClassName, generatedClass, "Forge");
    }

    private void generateFabricInitializer(Filer filer, TypeName mainClass, boolean isClient,
                                           String packageId, String addClassName) {

        String generatedClassName = getSimpleName(mainClass) + addClassName;

        ClassName fabricInterface = ClassName.get("net.fabricmc.api", isClient ? "ClientModInitializer" : "ModInitializer");
        String methodName = isClient ? "onInitializeClient" : "onInitialize";

        FieldSpec mainInstanceField = FieldSpec.builder(mainClass, "MAININSTANCE", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer("new $T()", mainClass)
                .build();

        MethodSpec getInstanceMethod = MethodSpec.methodBuilder("getInstance")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(mainClass)
                .addStatement("return MAININSTANCE")
                .build();

        MethodSpec initMethod = MethodSpec.methodBuilder(methodName)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(void.class)
                .addStatement("MAININSTANCE.init()")
                .build();

        TypeSpec generatedClass = TypeSpec.classBuilder(generatedClassName)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(fabricInterface)
                .addField(mainInstanceField)
                .addMethod(getInstanceMethod)
                .addMethod(initMethod)
                .build();

        writeClass(filer, packageId, generatedClassName, generatedClass, "Fabric");
    }

    private void writeClass(Filer filer, String packageId, String className, TypeSpec typeSpec, String loaderType) {
        try {
            JavaFile.builder(packageId, typeSpec)
                    .build()
                    .writeTo(filer);
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Error generating " + loaderType + " Initializer (" + className + "): " + e.getMessage());
        }
    }

    private String getSimpleName(TypeName typeName) {
        if (typeName instanceof ClassName className) {
            return className.simpleName();
        }
        String name = typeName.toString();
        return name.substring(name.lastIndexOf('.') + 1);
    }
}