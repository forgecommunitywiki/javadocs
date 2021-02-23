package fcw.tasks;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.AnnotationMemberDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.resolution.SymbolResolver;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.utils.SourceRoot;
import com.github.javaparser.utils.SourceRoot.Callback.Result;
import fcw.DocInfo;
import fcw.DocUtils;
import fcw.IdentifyingVisitor;
import fcw.ParserUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static fcw.DocInfo.ClassInfo;

public class ApplyDocs extends DefaultTask {
    @Input public File docsDir;
    @Input public File sourcesDir;
    @Input public Configuration configuration;
    @Input public String docFileExtension = ".json";

    @TaskAction
    public void act() throws IOException {

        SourceRoot sourceRoot = new SourceRoot(sourcesDir.toPath());
        Path docsRoot = docsDir.toPath();

        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        for (File jar : configuration.getFiles()) {
            typeSolver.add(new JarTypeSolver(jar));
        }

        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        sourceRoot.getParserConfiguration()
            .setSymbolResolver(symbolSolver)
            .setAttributeComments(true);
        sourceRoot.setPrinter(ParserUtils.PRINTER::print);

        sourceRoot.parse("", (local, absolute, result) -> {
            final CompilationUnit cu = result.getResult().orElseThrow(() -> new IllegalStateException(
                "Compilation error for file " + local + " under " + docsRoot + ": " + result.getProblems()));

            Path docsFileLocal = local.getParent()
                .resolve(local.getFileName().toString().replaceFirst("\\..*$", "") + docFileExtension);
            Path docsFile = docsRoot.resolve(docsFileLocal);

            if (Files.notExists(docsFile)) return Result.DONT_SAVE;

            DocInfo doc = DocInfo.read(docsFile);

            if (doc.classes.isEmpty()) return Result.DONT_SAVE;

            ApplyDocsVisitor visitor = new ApplyDocsVisitor(symbolSolver, doc);

            visitor.visit(cu);

            return visitor.modified ? Result.SAVE : Result.DONT_SAVE;
        });
    }

    static class ApplyDocsVisitor extends IdentifyingVisitor {
        private final DocInfo doc;
        private boolean modified = false;

        public ApplyDocsVisitor(SymbolResolver resolver, DocInfo doc) {
            super(resolver);
            this.doc = doc;
        }

        @Override
        protected void visitClass(TypeDeclaration<?> n, VisitContext ctx) {
            ClassInfo clsInfo = doc.classes.get(ctx.getQualifiedName());
            if (clsInfo != null && clsInfo.javadoc != null) {
                n.setComment(DocUtils.createComment(clsInfo.javadoc));
                modified = true;
            }
        }

        @Override
        protected void visitEnumConstant(EnumConstantDeclaration n, VisitContext ctx) {
            ClassInfo clsInfo = doc.classes.get(ctx.getQualifiedName());
            if (clsInfo != null) {
                ClassInfo.FieldInfo fieldInfo = clsInfo.fields.get(n.resolve().getName());
                if (fieldInfo != null && fieldInfo.javadoc != null) {
                    n.setComment(DocUtils.createComment(fieldInfo.javadoc));
                    modified = true;
                }
            }
        }

        @Override
        protected void visitField(FieldDeclaration n, VisitContext ctx) {
            ClassInfo clsInfo = doc.classes.get(ctx.getQualifiedName());
            if (clsInfo != null) {
                ClassInfo.FieldInfo fieldInfo = clsInfo.fields.get(n.resolve().getName());
                if (fieldInfo != null && fieldInfo.javadoc != null) {
                    n.setComment(DocUtils.createComment(fieldInfo.javadoc));
                    modified = true;
                }
            }
        }

        @Override
        protected void visitMethod(MethodDeclaration n, String descriptor, VisitContext ctx) {
            ClassInfo clsInfo = doc.classes.get(ctx.getQualifiedName());
            if (clsInfo != null) {
                String key = n.getNameAsString() + " " + descriptor;
                ClassInfo.MethodInfo methodInfo = clsInfo.methods.get(key);
                if (methodInfo != null && methodInfo.javadoc != null) {
                    n.setComment(DocUtils.createComment(methodInfo.javadoc));
                    modified = true;
                }
            }
        }

        @Override
        protected void visitConstructor(ConstructorDeclaration n, String descriptor, VisitContext ctx) {
            ClassInfo clsInfo = doc.classes.get(ctx.getQualifiedName());
            if (clsInfo != null) {
                String key = n.getNameAsString() + " " + descriptor;
                ClassInfo.MethodInfo methodInfo = clsInfo.methods.get(key);
                if (methodInfo != null && methodInfo.javadoc != null) {
                    n.setComment(DocUtils.createComment(methodInfo.javadoc));
                    modified = true;
                }
            }
        }

        @Override
        protected void visitAnnotationMember(AnnotationMemberDeclaration n, String descriptor, VisitContext ctx) {
            ClassInfo clsInfo = doc.classes.get(ctx.getQualifiedName());
            if (clsInfo != null) {
                String key = n.getNameAsString() + " " + descriptor;
                ClassInfo.MethodInfo methodInfo = clsInfo.methods.get(key);
                if (methodInfo != null && methodInfo.javadoc != null) {
                    n.setComment(DocUtils.createComment(methodInfo.javadoc));
                    modified = true;
                }
            }
        }
    }
}
