package v.akfz.db.generator;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;

import v.akfz.db.annotation.LoaderGuide;

@SupportedAnnotationTypes("v.akfz.db.generator.GenerateInitializer")
@SupportedOptions("modLoaderTarget")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class InitializerProcessor extends AbstractProcessor {

    private static final Pattern IMPORT_LINE = Pattern.compile("^import\\s+[\\w.*]+\\s*;$", Pattern.MULTILINE);

    private final Set<String> generatedClasses = new HashSet<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) return true;

        Filer filer = processingEnv.getFiler();
        String currentTarget = processingEnv.getOptions().get("modLoaderTarget");

        if (currentTarget == null
                || "common".equalsIgnoreCase(currentTarget)
                || "none".equalsIgnoreCase(currentTarget)) {
            return true;
        }

        boolean isFabric = "fabric".equalsIgnoreCase(currentTarget);
        boolean isForge = "forge".equalsIgnoreCase(currentTarget);
        boolean isNeoForge = "neoforge".equalsIgnoreCase(currentTarget);

        for (Element element : roundEnv.getElementsAnnotatedWith(GenerateInitializer.class)) {
            if (!(element instanceof TypeElement typeElement)) continue;

            GenerateInitializer ann = typeElement.getAnnotation(GenerateInitializer.class);

            LoaderType loader = ann.loader();
            String modId = ann.modId();
            boolean isClient = ann.isClient();
            String packageId = ann.packageStr();

            TypeName mainClass = getMainClassTypeName(ann, typeElement);

            String endPackage = (packageId.isEmpty() || "null".equalsIgnoreCase(packageId))
                    ? processingEnv.getElementUtils().getPackageOf(typeElement).getQualifiedName().toString()
                    : packageId;

            boolean allowsFabric = loader == null
                    || loader == LoaderType.FabricLike
                    || loader == LoaderType.Both;
            boolean allowsForge = loader == null
                    || loader == LoaderType.ForgeLike
                    || loader == LoaderType.Both;

            boolean hasInit = hasInitMethod(typeElement);
            if (!hasInit && ann.guides().length == 0) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.WARNING,
                        "Class '" + typeElement.getQualifiedName()
                                + "' is annotated with @GenerateInitializer, "
                                + "but missing 'public void init()' method and no guides provided!",
                        typeElement);
            }

            if (isFabric && allowsFabric) {
                List<GuideMethod> guides = collectGuides(ann, "fabric");
                generateFabricInitializer(filer, mainClass, isClient, endPackage,
                        ann.addClassNameFabric(), hasInit, guides);
            }

            if ((isForge || isNeoForge) && allowsForge) {
                String target = isNeoForge ? "neoforge" : "forge";
                List<GuideMethod> guides = collectGuides(ann, target);
                generateForgeInitializer(filer, mainClass, modId, isClient, endPackage,
                        ann.addClassNameForge(), isNeoForge, hasInit, guides);
            }
        }
        return true;
    }

    private record GuideMethod(String methodName, String body, List<String> imports) {}

    private List<GuideMethod> collectGuides(GenerateInitializer ann, String target) {
        List<TypeMirror> guideTypes = new ArrayList<>();
        try {
            Class<?>[] ignored = ann.guides();
        } catch (MirroredTypesException e) {
            guideTypes.addAll(e.getTypeMirrors());
        }

        List<GuideMethod> out = new ArrayList<>();
        if (guideTypes.isEmpty()) return out;

        Trees trees;
        try {
            trees = Trees.instance(processingEnv);
        } catch (Throwable t) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.WARNING,
                    "LoaderGuide requires Javac (com.sun.source.util.Trees not available).");
            return out;
        }

        for (TypeMirror tm : guideTypes) {
            if (!(tm instanceof DeclaredType dt)) continue;
            TypeElement guide = (TypeElement) dt.asElement();
            String guideSimple = guide.getSimpleName().toString();

            TreePath path = trees.getPath(guide);
            if (path == null) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "Cannot access source of " + guide.getQualifiedName() + " (Javac only).");
                continue;
            }
            if (!(path.getLeaf() instanceof ClassTree classTree)) continue;

            for (Element enclosed : guide.getEnclosedElements()) {
                if (enclosed.getKind() != ElementKind.METHOD) continue;
                LoaderGuide lg = enclosed.getAnnotation(LoaderGuide.class);
                if (lg == null) continue;

                boolean matches = false;
                for (String l : lg.value()) {
                    if (l.equalsIgnoreCase(target)) { matches = true; break; }
                }
                if (!matches) continue;

                ExecutableElement ex = (ExecutableElement) enclosed;
                if (!ex.getParameters().isEmpty()) {
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.ERROR,
                            "LoaderGuide method must take no parameters: "
                                    + guide.getQualifiedName() + "#" + ex.getSimpleName(),
                            enclosed);
                    continue;
                }

                MethodTree mt = null;
                for (Tree member : classTree.getMembers()) {
                    if (member instanceof MethodTree m
                            && m.getName().contentEquals(ex.getSimpleName())) {
                        mt = m;
                        break;
                    }
                }
                if (mt == null || mt.getBody() == null) {
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.ERROR,
                            "Cannot read body of " + guide.getQualifiedName()
                                    + "#" + ex.getSimpleName(),
                            enclosed);
                    continue;
                }

                String src = mt.getBody().toString();
                String body = src.substring(1, src.length() - 1).trim();

                Set<String> imports = new LinkedHashSet<>();
                for (ImportTree imp : path.getCompilationUnit().getImports()) {
                    imports.add("import " + imp.getQualifiedIdentifier() + ";");
                }

                String methodName = guideSimple + "_" + ex.getSimpleName();
                out.add(new GuideMethod(methodName, body, new ArrayList<>(imports)));
            }
        }
        return out;
    }

    private TypeName getMainClassTypeName(GenerateInitializer annotation, TypeElement annotatedElement) {
        try {
            Class<?> clazz = annotation.mainClass();
            return NotAClass.class.equals(clazz)
                    ? ClassName.get(annotatedElement)
                    : ClassName.get(clazz);
        } catch (MirroredTypeException mte) {
            TypeMirror typeMirror = mte.getTypeMirror();
            if (typeMirror instanceof DeclaredType declaredType) {
                Element element = declaredType.asElement();
                if (element instanceof TypeElement te
                        && NotAClass.class.getName().equals(te.getQualifiedName().toString())) {
                    return ClassName.get(annotatedElement);
                }
            }
            return TypeName.get(typeMirror);
        }
    }

    private void generateForgeInitializer(Filer filer, TypeName mainClass, String modId, boolean isClient,
                                          String packageId, String addClassName, boolean isNeoForge,
                                          boolean hasInit, List<GuideMethod> guides) {

        String generatedClassName = getSimpleName(mainClass) + addClassName;
        String fullClassName = packageId + "." + generatedClassName;
        if (!generatedClasses.add(fullClassName)) return;

        boolean useGuides = !guides.isEmpty();
        boolean useMain = hasInit && !useGuides;

        String basePkg = isNeoForge ? "net.neoforged" : "net.minecraftforge";
        ClassName modAnnotation = ClassName.get(basePkg + ".fml.common", "Mod");

        MethodSpec.Builder ctor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(generatedClassName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(modAnnotation)
                        .addMember("value", "$S", modId).build());

        if (useMain) {
            FieldSpec mainInstanceField = FieldSpec.builder(mainClass, "MAININSTANCE",
                            Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                    .initializer("new $T()", mainClass)
                    .build();

            MethodSpec getInstanceMethod = MethodSpec.methodBuilder("getInstance")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .returns(mainClass)
                    .addStatement("return MAININSTANCE")
                    .build();

            classBuilder.addField(mainInstanceField);
            classBuilder.addMethod(getInstanceMethod);
        }

        if (useGuides) {
            for (GuideMethod g : guides) {
                classBuilder.addMethod(MethodSpec.methodBuilder(g.methodName())
                        .addModifiers(Modifier.PRIVATE)
                        .addCode(CodeBlock.of("$L", g.body()))
                        .build());
                ctor.addStatement("$N()", g.methodName());
            }
        } else if (hasInit) {
            ClassName contextClass = ClassName.get(basePkg + ".fml.javafmlmod", "FMLJavaModLoadingContext");
            ClassName setupEventClass = ClassName.get(basePkg + ".fml.event.lifecycle",
                    isClient ? "FMLClientSetupEvent" : "FMLCommonSetupEvent");

            MethodSpec setupMethod = MethodSpec.methodBuilder("onSetup")
                    .addModifiers(Modifier.PRIVATE)
                    .addParameter(setupEventClass, "event")
                    .addStatement("MAININSTANCE.init()")
                    .build();

            ctor.addStatement("$T.get().getModEventBus().addListener(this::onSetup)", contextClass);
            classBuilder.addMethod(setupMethod);
        }

        classBuilder.addMethod(ctor.build());
        writeClass(filer, packageId, generatedClassName, classBuilder.build(), "Forge", guides);
    }

    private void generateFabricInitializer(Filer filer, TypeName mainClass, boolean isClient,
                                           String packageId, String addClassName,
                                           boolean hasInit, List<GuideMethod> guides) {

        String generatedClassName = getSimpleName(mainClass) + addClassName;
        String fullClassName = packageId + "." + generatedClassName;
        if (!generatedClasses.add(fullClassName)) return;

        boolean useGuides = !guides.isEmpty();

        ClassName fabricInterface = ClassName.get("net.fabricmc.api",
                isClient ? "ClientModInitializer" : "ModInitializer");
        String methodName = isClient ? "onInitializeClient" : "onInitialize";

        MethodSpec.Builder initMethodBuilder = MethodSpec.methodBuilder(methodName)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(void.class);

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(generatedClassName)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(fabricInterface);

        if (!useGuides) {
            FieldSpec mainInstanceField = FieldSpec.builder(mainClass, "MAININSTANCE",
                            Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                    .initializer("new $T()", mainClass)
                    .build();

            MethodSpec getInstanceMethod = MethodSpec.methodBuilder("getInstance")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .returns(mainClass)
                    .addStatement("return MAININSTANCE")
                    .build();

            classBuilder.addField(mainInstanceField);
            classBuilder.addMethod(getInstanceMethod);
        }

        if (useGuides) {
            for (GuideMethod g : guides) {
                classBuilder.addMethod(MethodSpec.methodBuilder(g.methodName())
                        .addModifiers(Modifier.PRIVATE)
                        .addCode(CodeBlock.of("$L", g.body()))
                        .build());
                initMethodBuilder.addStatement("$N()", g.methodName());
            }
        } else if (hasInit) {
            initMethodBuilder.addStatement("MAININSTANCE.init()");
        }

        classBuilder.addMethod(initMethodBuilder.build());
        writeClass(filer, packageId, generatedClassName, classBuilder.build(), "Fabric", guides);
    }

    private void writeClass(Filer filer, String packageId, String className,
                            TypeSpec typeSpec, String loaderType, List<GuideMethod> guides) {
        String source = JavaFile.builder(packageId, typeSpec).build().toString();

        if (guides != null && !guides.isEmpty()) {
            Set<String> existing = new LinkedHashSet<>();
            Matcher m = IMPORT_LINE.matcher(source);
            while (m.find()) existing.add(m.group());

            Set<String> extra = new LinkedHashSet<>();
            for (GuideMethod g : guides) extra.addAll(g.imports());
            extra.removeAll(existing);

            if (!extra.isEmpty()) {
                int pkgSemi = source.indexOf(';', source.indexOf("package "));
                int afterPkg = source.indexOf('\n', pkgSemi);
                if (afterPkg > 0) {
                    String block = "\n" + String.join("\n", extra) + "\n";
                    source = source.substring(0, afterPkg + 1)
                            + block
                            + source.substring(afterPkg + 1);
                }
            }
        }

        try {
            JavaFileObject file = filer.createSourceFile(packageId + "." + className);
            try (Writer w = file.openWriter()) {
                w.write(source);
            }
        } catch (FilerException ignored) {
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Error generating " + loaderType + " Initializer (" + className + "): "
                            + e.getMessage());
        }
    }

    private String getSimpleName(TypeName typeName) {
        if (typeName instanceof ClassName className) return className.simpleName();
        String name = typeName.toString();
        return name.substring(name.lastIndexOf('.') + 1);
    }

    private boolean hasInitMethod(TypeElement typeElement) {
        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.METHOD) continue;
            if (!enclosed.getSimpleName().contentEquals("init")) continue;

            ExecutableElement method = (ExecutableElement) enclosed;
            if (method.getParameters().isEmpty()
                    && method.getModifiers().contains(Modifier.PUBLIC)) {
                return true;
            }
        }
        return false;
    }
}