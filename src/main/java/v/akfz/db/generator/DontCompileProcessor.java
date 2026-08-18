package v.akfz.db.generator;

import java.util.Optional;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import com.sun.source.util.Trees;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.TreeTranslator;

import v.akfz.db.annotation.DontCompile;

/**
 * Processor of {@link DontCompile}
 */
@SupportedAnnotationTypes("v.akfz.db.annotation.DontCompile")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class DontCompileProcessor extends AbstractProcessor {

    private Trees trees;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.trees = Trees.instance(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(DontCompile.class)) {
            if (!(element instanceof TypeElement)) continue;

            DontCompile annotation = element.getAnnotation(DontCompile.class);
            boolean keepInTest = annotation == null || annotation.value();

            JCTree tree = (JCTree) trees.getTree(element);
            JCCompilationUnit cu = (JCCompilationUnit) trees.getPath(element).getCompilationUnit();

            if (keepInTest && isTestExecution(cu)) {
                continue;
            }

            cu.accept(new TreeTranslator() {
                @Override
                public void visitClassDef(JCTree.JCClassDecl treeDecl) {
                    if (treeDecl.equals(tree)) {
                        this.result = null;
                    } else {
                        super.visitClassDef(treeDecl);
                    }
                }
            });
        }
        return true;
    }

    private boolean isTestExecution(JCCompilationUnit cu) {
        if (cu != null && cu.getSourceFile() != null) {
            String sourcePath = cu.getSourceFile().toUri().getPath().toLowerCase();
            if (sourcePath.contains("/src/test/") || sourcePath.contains("\\src\\test\\")) {
                return true;
            }
        }

        try {
            FileObject dummy = processingEnv.getFiler().createResource(
                    StandardLocation.CLASS_OUTPUT, "", "chk_" + System.currentTimeMillis());
            String outputPath = dummy.toUri().getPath().toLowerCase();
            dummy.delete();
            if (outputPath.contains("/test/") || outputPath.contains("\\test\\") || outputPath.contains("testjava")) {
                return true;
            }
        } catch (Exception ignored) {}

        String sunCmd = System.getProperty("sun.java.command", "").toLowerCase();
        if (sunCmd.contains("test") || sunCmd.contains("junit") || sunCmd.contains("surefire") || sunCmd.contains("idea_rt")) {
            return true;
        }

        if (System.getProperty("org.gradle.test.worker") != null || System.getProperty("idea.test.cyclic.buffer.size") != null) {
            return true;
        }

        try {
            Optional<ProcessHandle> ph = Optional.of(ProcessHandle.current());
            while (ph.isPresent()) {
                String cmd = ph.get().info().commandLine().orElse("").toLowerCase();
                if (cmd.contains("test") || cmd.contains("check") || cmd.contains("junit") || cmd.contains("surefire") || cmd.contains("idea_rt")) {
                    return true;
                }
                ph = ph.get().parent();
            }
        } catch (Exception ignored) {}

        return false;
    }
}