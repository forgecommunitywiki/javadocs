package fcw.tasks;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.AnnotationMemberDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.utils.SourceRoot;
import com.github.javaparser.utils.SourceRoot.Callback.Result;
import fcw.DocInfo;
import fcw.DocUtils;
import fcw.ParserUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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

        sourceRoot.parse("", (local, absolute, result) -> {
            final CompilationUnit cu = result.getResult().orElseThrow(() -> new IllegalStateException(
                "Compilation error for file " + local + " under " + docsRoot + ": " + result.getProblems()));

            Path docsFileLocal = local.getParent()
                .resolve(local.getFileName().toString().replaceFirst("\\..*$", "") + docFileExtension);
            Path docsFile = docsRoot.resolve(docsFileLocal);

            if (Files.notExists(docsFile)) return Result.DONT_SAVE;

            DocInfo doc = DocInfo.read(docsFile);

            if (doc.classes.isEmpty()) return Result.DONT_SAVE;

            AtomicBoolean modified = new AtomicBoolean(false);

            AtomicInteger anonClassCount = new AtomicInteger(1);
            for (TypeDeclaration<?> type : cu.getTypes()) {
                final String fqn = ParserUtils.toFQN(type.resolve());

                ClassInfo clsInfo = doc.classes.get(fqn);
                if (clsInfo == null) continue;
                if (clsInfo.javadoc != null) {
                    type.setComment(DocUtils.createComment(clsInfo.javadoc));
                    modified.set(true);
                }

                visitBodyDeclarations(type.getMembers(), fqn, doc, symbolSolver, modified, anonClassCount);
            }

            return modified.get() ? Result.SAVE : Result.DONT_SAVE;
        });
    }

    private void visitBodyDeclarations(NodeList<BodyDeclaration<?>> nodeList, String fqn, DocInfo doc,
        JavaSymbolSolver symbolSolver, AtomicBoolean modified, AtomicInteger anonymousClassCount) {

        ClassInfo clsInfo = doc.classes.get(fqn);
        for (BodyDeclaration<?> bodyDecl : nodeList) {
            if (bodyDecl instanceof FieldDeclaration) {
                FieldDeclaration field = (FieldDeclaration) bodyDecl;

                ClassInfo.FieldInfo fieldInfo = clsInfo.fields.get(field.resolve().getName());
                if (fieldInfo != null && fieldInfo.javadoc != null) {
                    field.setComment(DocUtils.createComment(fieldInfo.javadoc));
                    modified.set(true);
                }
            } else if (bodyDecl instanceof EnumConstantDeclaration) {
                EnumConstantDeclaration enumConstant = (EnumConstantDeclaration) bodyDecl;

                ClassInfo.FieldInfo fieldInfo = clsInfo.fields.get(enumConstant.resolve().getName());
                if (fieldInfo != null && fieldInfo.javadoc != null) {
                    enumConstant.setComment(DocUtils.createComment(fieldInfo.javadoc));
                    modified.set(true);
                }
            } else if (bodyDecl instanceof MethodDeclaration) {
                MethodDeclaration method = (MethodDeclaration) bodyDecl;

                String key = method.getNameAsString() + " " + ParserUtils.toDescriptor(symbolSolver, method);
                ClassInfo.MethodInfo methodInfo = clsInfo.methods.get(key);
                if (methodInfo != null && methodInfo.javadoc != null) {
                    method.setComment(DocUtils.createComment(methodInfo.javadoc));
                    modified.set(true);
                }
            } else if (bodyDecl instanceof ConstructorDeclaration) {
                ConstructorDeclaration constructor = (ConstructorDeclaration) bodyDecl;

                String key = constructor.getNameAsString() + " " + ParserUtils.toDescriptor(symbolSolver, constructor);
                ClassInfo.MethodInfo ctorInfo = clsInfo.methods.get(key);
                if (ctorInfo != null && ctorInfo.javadoc != null) {
                    constructor.setComment(DocUtils.createComment(ctorInfo.javadoc));
                    modified.set(true);
                }
            } else if (bodyDecl instanceof AnnotationMemberDeclaration) {
                AnnotationMemberDeclaration annotationMember = (AnnotationMemberDeclaration) bodyDecl;

                String key = annotationMember.getNameAsString() + " " + ParserUtils
                    .toDescriptor(symbolSolver, annotationMember);
                ClassInfo.MethodInfo methodInfo = clsInfo.methods.get(key);
                if (methodInfo != null && methodInfo.javadoc != null) {
                    annotationMember.setComment(DocUtils.createComment(methodInfo.javadoc));
                    modified.set(true);
                }
            }
            bodyDecl.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(ObjectCreationExpr expr, Void arg) {
                    expr.getAnonymousClassBody().ifPresent(nodes -> {
                        String anonFQN = fqn + "$" + anonymousClassCount.getAndIncrement();

                        ClassInfo clsInfo = doc.classes.get(anonFQN);
                        if (clsInfo == null) return;

                        visitBodyDeclarations(nodes, anonFQN, doc, symbolSolver, modified, new AtomicInteger(1));

                    });
                    super.visit(expr, arg);
                }

                @Override
                public void visit(ClassOrInterfaceDeclaration n, Void arg) {} // No-op to not recurse within

                @Override
                public void visit(EnumDeclaration n, Void arg) {} // No-op to not recurse within

                @Override
                public void visit(AnnotationDeclaration n, Void arg) {} // No-op to not recurse within
            }, null);
        }

    }
}
