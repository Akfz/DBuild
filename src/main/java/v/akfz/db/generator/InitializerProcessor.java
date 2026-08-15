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

@SupportedAnnotationTypes("v.akfz.db.generator.GenerateInitializer")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class InitializerProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Filer filer = processingEnv.getFiler();

        for (Element element : roundEnv.getElementsAnnotatedWith(GenerateInitializer.class)) {
            if (element instanceof TypeElement typeElement) {
                GenerateInitializer annotation = typeElement.getAnnotation(GenerateInitializer.class);

                LoaderType loader = annotation.loader();
                String modId = annotation.modId();
                boolean isClient = annotation.isClient();
                String packageId = annotation.packageStr();
                String addClassFg = annotation.addClassNameForge();
                String addClassFb = annotation.addClassNameFabric();

                TypeName mainClassName = getMainClassTypeName(annotation, typeElement);

                String endPackage = (packageId.isEmpty() || packageId.equalsIgnoreCase("null"))
                        ? processingEnv.getElementUtils().getPackageOf(typeElement).getQualifiedName().toString()
                        : packageId;

                String currentTarget = processingEnv.getOptions().get("modLoaderTarget");
                boolean isNeoForge = currentTarget != null && currentTarget.equalsIgnoreCase("neoforge");

                boolean targetAllowsFabric = currentTarget == null || currentTarget.equalsIgnoreCase("fabric");
                boolean targetAllowsForge = currentTarget == null || currentTarget.equalsIgnoreCase("forge") || isNeoForge;

                boolean loaderAllowsFabric = loader == null || loader == LoaderType.FabricLike || loader == LoaderType.Both;
                boolean loaderAllowsForge = loader == null || loader == LoaderType.ForgeLike || loader == LoaderType.Both;

                boolean shouldGenerateFabric = targetAllowsFabric && loaderAllowsFabric;
                boolean shouldGenerateForge = targetAllowsForge && loaderAllowsForge;

                if (shouldGenerateFabric) {
                    generateFabricInitializer(filer, mainClassName, isClient, endPackage, addClassFb);
                }

                if (shouldGenerateForge) {
                    generateForgeInitializer(filer, mainClassName, modId, isClient, endPackage, addClassFg, isNeoForge);
                }
            }
        }
        return true;
    }

    private TypeName getMainClassTypeName(GenerateInitializer annotation, TypeElement annotatedElement) {
        try {
            Class<?> clazz = annotation.mainClass();
            if (clazz.getName().equals("v.akfz.db.generator.NotAClass")) {
                return ClassName.get(annotatedElement);
            }
            return ClassName.get(clazz);
        } catch (MirroredTypeException mte) {
            TypeMirror typeMirror = mte.getTypeMirror();
            if (typeMirror.toString().equals("v.akfz.db.generator.NotAClass")) {
                return ClassName.get(annotatedElement);
            }
            return TypeName.get(typeMirror);
        }
    }

    private void generateForgeInitializer(Filer filer, TypeName mainClass, String modId, boolean isClient,
            String packageId, String addClassName, boolean isNeoForge) {

        String mainSimpleName = getSimpleName(mainClass);
        String generatedClassName = mainSimpleName + addClassName;

        ClassName modAnnotation = isNeoForge
                ? ClassName.get("net.neoforged.fml.common", "Mod")
                : ClassName.get("net.minecraftforge.fml.common", "Mod");

        ClassName distClass = isNeoForge
                ? ClassName.get("net.neoforged.api.distmarker", "Dist")
                : ClassName.get("net.minecraftforge.api.distmarker", "Dist");

        ClassName fmlEnvClass = isNeoForge
                ? ClassName.get("net.neoforged.fml.loading", "FMLEnvironment")
                : ClassName.get("net.minecraftforge.fml.loading", "FMLEnvironment");

        FieldSpec mainInstanceField = FieldSpec.builder(mainClass, "MAININSTANCE", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer("new $T()", mainClass)
                .build();

        MethodSpec getInstanceMethod = MethodSpec.methodBuilder("getInstance")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(mainClass)
                .addStatement("return MAININSTANCE")
                .build();

        MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC);

        if (isClient) {
            constructorBuilder.beginControlFlow("if ($T.dist == $T.CLIENT)", fmlEnvClass, distClass)
                    .addStatement("MAININSTANCE.init()")
                    .endControlFlow();
        } else {
            constructorBuilder.addStatement("MAININSTANCE.init()");
        }

        TypeSpec generatedClass = TypeSpec.classBuilder(generatedClassName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(modAnnotation).addMember("value", "$S", modId).build())
                .addField(mainInstanceField)
                .addMethod(getInstanceMethod)
                .addMethod(constructorBuilder.build())
                .build();

        try {
            JavaFile.builder(packageId, generatedClass)
                    .build()
                    .writeTo(filer);
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Error generating Forge Initializer (" + generatedClassName + "): " + e.getMessage());
        }
    }

    private void generateFabricInitializer(Filer filer, TypeName mainClass, boolean isClient,
            String packageId, String addClassName) {

        String mainSimpleName = getSimpleName(mainClass);
        String generatedClassName = mainSimpleName + addClassName;

        ClassName fabricInterface = isClient
                ? ClassName.get("net.fabricmc.api", "ClientModInitializer")
                : ClassName.get("net.fabricmc.api", "ModInitializer");

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

        try {
            JavaFile.builder(packageId, generatedClass)
                    .build()
                    .writeTo(filer);
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Error generating Fabric Initializer (" + generatedClassName + "): " + e.getMessage());
        }
    }

    private String getSimpleName(TypeName typeName) {
        if (typeName instanceof ClassName className) {
            return className.simpleName();
        }
        String stringName = typeName.toString();
        return stringName.substring(stringName.lastIndexOf('.') + 1);
    }
}
