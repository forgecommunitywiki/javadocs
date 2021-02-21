package fcw.tasks;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.utils.SourceRoot;
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

            if (Files.notExists(docsFile)) return SourceRoot.Callback.Result.DONT_SAVE;

            DocInfo doc = DocInfo.read(docsFile);

            if (doc.classes.isEmpty()) return SourceRoot.Callback.Result.DONT_SAVE;

            cu.accept(new ModifierVisitor<Void>() {
                @Override
                public Visitable visit(ClassOrInterfaceDeclaration n, Void arg) {
                    String fqn = ParserUtils.toFQN(n);
                    ClassInfo clsInfo = doc.classes.get(fqn);
                    if (clsInfo != null && clsInfo.javadoc != null) {
                        n.setComment(DocUtils.createComment(clsInfo.javadoc));
                    }
                    return super.visit(n, arg);
                }

                @Override
                public Visitable visit(EnumDeclaration n, Void arg) {
                    String fqn = ParserUtils.toFQN(n.resolve());
                    ClassInfo clsInfo = doc.classes.get(fqn);
                    if (clsInfo != null && clsInfo.javadoc != null) {
                        n.setComment(DocUtils.createComment(clsInfo.javadoc));
                    }
                    return super.visit(n, arg);
                }

                @Override
                public Visitable visit(FieldDeclaration n, Void arg) {
                    final TypeDeclaration<?> classDeclaration = n.getParentNode()
                        .filter(TypeDeclaration.class::isInstance)
                        .map(TypeDeclaration.class::cast)
                        .orElse(null);
                    if (classDeclaration == null) return super.visit(n, arg);
                    // TODO: fields can be in anonymous classes.

                    String fqn = ParserUtils.toFQN(classDeclaration.resolve());
                    ClassInfo clsInfo = doc.classes.get(fqn);
                    if (clsInfo != null) {
                        ClassInfo.FieldInfo fieldInfo = clsInfo.fields.get(n.resolve().getName());
                        if (fieldInfo != null && fieldInfo.javadoc != null) {
                            n.setComment(DocUtils.createComment(fieldInfo.javadoc));
                        }
                    }
                    return super.visit(n, arg);
                }

                @Override
                public Visitable visit(EnumConstantDeclaration n, Void arg) {
                    final EnumDeclaration classDeclaration = n.getParentNode()
                        .filter(EnumDeclaration.class::isInstance)
                        .map(EnumDeclaration.class::cast)
                        .orElse(null);
                    if (classDeclaration == null) return super.visit(n, arg);

                    String fqn = ParserUtils.toFQN(classDeclaration.resolve());
                    ClassInfo clsInfo = doc.classes.get(fqn);
                    if (clsInfo != null) {
                        ClassInfo.FieldInfo fieldInfo = clsInfo.fields.get(n.resolve().getName());
                        if (fieldInfo != null && fieldInfo.javadoc != null) {
                            n.setComment(DocUtils.createComment(fieldInfo.javadoc));
                        }
                    }
                    return super.visit(n, arg);
                }

                @Override
                public Visitable visit(MethodDeclaration n, Void arg) {
                    final TypeDeclaration<?> classDeclaration = n.getParentNode()
                        .filter(TypeDeclaration.class::isInstance)
                        .map(TypeDeclaration.class::cast)
                        .orElse(null);
                    if (classDeclaration == null) return super.visit(n, arg);
                    // TODO: methods can be in anonymous classes.

                    String fqn = ParserUtils.toFQN(classDeclaration.resolve());
                    ClassInfo clsInfo = doc.classes.get(fqn);
                    if (clsInfo != null) {
                        String key = n.getNameAsString() + " " + ParserUtils.toDescriptor(symbolSolver, n);
                        ClassInfo.MethodInfo methodInfo = clsInfo.methods.get(key);
                        if (methodInfo != null && methodInfo.javadoc != null) {
                            n.setComment(DocUtils.createComment(methodInfo.javadoc));
                        }
                    }
                    return super.visit(n, arg);
                }

                @Override
                public Visitable visit(ConstructorDeclaration n, Void arg) {
                    final TypeDeclaration<?> classDeclaration = n.getParentNode()
                        .filter(TypeDeclaration.class::isInstance)
                        .map(TypeDeclaration.class::cast)
                        .orElse(null);
                    if (classDeclaration == null) return super.visit(n, arg);
                    // TODO: methods can be in anonymous classes.

                    String fqn = ParserUtils.toFQN(classDeclaration.resolve());
                    ClassInfo clsInfo = doc.classes.get(fqn);
                    if (clsInfo != null) {
                        String key = n.getNameAsString() + " " + ParserUtils.toDescriptor(symbolSolver, n);
                        ClassInfo.MethodInfo methodInfo = clsInfo.methods.get(key);
                        if (methodInfo != null && methodInfo.javadoc != null) {
                            n.setComment(DocUtils.createComment(methodInfo.javadoc));
                        }
                    }
                    return super.visit(n, arg);
                }
            }, null);

            return SourceRoot.Callback.Result.SAVE;
        });
    }
}
