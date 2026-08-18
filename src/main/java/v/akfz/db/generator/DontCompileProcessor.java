package v.akfz.db.generator;

import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import com.sun.source.util.Trees;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.TreeTranslator;
import v.akfz.db.annotation.DontCompile;

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
            if (element instanceof TypeElement) {
                JCTree tree = (JCTree) trees.getTree(element);
                JCCompilationUnit cu = (JCCompilationUnit) trees.getPath(element).getCompilationUnit();

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
        }
        return true;
    }
}