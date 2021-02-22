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
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static fcw.DocInfo.ClassInfo;

public class MakeDocs extends DefaultTask {
    @Input public File docsDir;
    @Input public File sourcesDir;
    @Input public Configuration configuration;
    @Input public String docFileExtension = ".json";

    @TaskAction
    public void act() throws IOException {
        SourceRoot sourceRoot = new SourceRoot(sourcesDir.toPath());
        Path docsRoot = docsDir.toPath();

        if (Files.isDirectory(docsRoot)) {
            try (Stream<Path> walk = Files.walk(docsRoot)) {
                //noinspection ResultOfMethodCallIgnored
                walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            }
        } else if (Files.notExists(docsRoot)) {
            Files.createDirectories(docsRoot);
        }

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

            DocInfo doc = new DocInfo();

            if (cu.getAllComments().isEmpty()) return SourceRoot.Callback.Result.DONT_SAVE;

            AtomicInteger anonClassCount = new AtomicInteger(1);
            for (TypeDeclaration<?> type : cu.getTypes()) {
                final String fqn = ParserUtils.toFQN(type.resolve());

                type.getJavadocComment().map(DocUtils::parseComment).ifPresent(javadoc -> {
                    ClassInfo classInfo = new ClassInfo(fqn);
                    classInfo.javadoc = javadoc;
                    doc.classes.put(fqn, classInfo);
                });

                visitBodyDeclarations(fqn, type.getMembers(), doc, symbolSolver, anonClassCount);
            }

            if (!doc.classes.isEmpty()) {
                Path docsFileLocal = local.getParent()
                    .resolve(local.getFileName().toString().replaceFirst("\\..*$", "") + docFileExtension);
                Path docsFile = docsRoot.resolve(docsFileLocal);
                try {
                    Files.deleteIfExists(docsFile);
                } catch (IOException e) {
                    throw new RuntimeException("Exception while trying to delete existing file " + docsFile, e);
                }
                try {
                    Files.createDirectories(docsFile.getParent());
                } catch (IOException e) {
                    throw new RuntimeException("Exception while trying to create parent directories for file " + docsFile, e);
                }
                doc.write(docsFile);
            }

            return SourceRoot.Callback.Result.DONT_SAVE;
        });
    }

    private void visitBodyDeclarations(String fqn, NodeList<BodyDeclaration<?>> nodeList, DocInfo doc,
        JavaSymbolSolver symbolSolver, AtomicInteger anonymousClassCount) {

        for (BodyDeclaration<?> bodyDecl : nodeList) {
            if (bodyDecl instanceof FieldDeclaration) {
                FieldDeclaration field = (FieldDeclaration) bodyDecl;

                field.getJavadocComment().map(DocUtils::parseComment).ifPresent(javadoc -> {
                    ClassInfo info = doc.classes.computeIfAbsent(fqn, ClassInfo::new);

                    ClassInfo.FieldInfo fieldInfo = new ClassInfo.FieldInfo(field.resolve().getName());
                    fieldInfo.javadoc = javadoc;

                    info.fields.put(fieldInfo.name, fieldInfo);
                });
            } else if (bodyDecl instanceof EnumConstantDeclaration) {
                EnumConstantDeclaration enumConstant = (EnumConstantDeclaration) bodyDecl;

                enumConstant.getJavadocComment().map(DocUtils::parseComment).ifPresent(javadoc -> {
                    ClassInfo info = doc.classes.computeIfAbsent(fqn, ClassInfo::new);

                    ClassInfo.FieldInfo fieldInfo = new ClassInfo.FieldInfo(enumConstant.resolve().getName());
                    fieldInfo.javadoc = javadoc;

                    info.fields.put(fieldInfo.name, fieldInfo);
                });
            } else if (bodyDecl instanceof MethodDeclaration) {
                MethodDeclaration method = (MethodDeclaration) bodyDecl;

                method.getJavadocComment().map(DocUtils::parseComment).ifPresent(javadoc -> {
                    ClassInfo info = doc.classes.computeIfAbsent(fqn, ClassInfo::new);

                    ClassInfo.MethodInfo methodInfo = new ClassInfo.MethodInfo(
                        method.getNameAsString(), ParserUtils.toDescriptor(symbolSolver, method));
                    methodInfo.javadoc = javadoc;

                    info.methods.put(methodInfo.name + " " + methodInfo.descriptor, methodInfo);
                });
            } else if (bodyDecl instanceof ConstructorDeclaration) {
                ConstructorDeclaration constructor = (ConstructorDeclaration) bodyDecl;

                constructor.getJavadocComment().map(DocUtils::parseComment).ifPresent(javadoc -> {
                    ClassInfo info = doc.classes.computeIfAbsent(fqn, ClassInfo::new);

                    ClassInfo.MethodInfo methodInfo = new ClassInfo.MethodInfo(
                        constructor.getNameAsString(), ParserUtils.toDescriptor(symbolSolver, constructor));
                    methodInfo.javadoc = javadoc;

                    info.methods.put(methodInfo.name + " " + methodInfo.descriptor, methodInfo);
                });
            } else if (bodyDecl instanceof AnnotationMemberDeclaration) {
                AnnotationMemberDeclaration annotationMember = (AnnotationMemberDeclaration) bodyDecl;

                annotationMember.getJavadocComment().map(DocUtils::parseComment).ifPresent(javadoc -> {
                    ClassInfo info = doc.classes.computeIfAbsent(fqn, ClassInfo::new);

                    ClassInfo.MethodInfo methodInfo = new ClassInfo.MethodInfo(
                        annotationMember.getNameAsString(), ParserUtils.toDescriptor(symbolSolver, annotationMember));
                    methodInfo.javadoc = javadoc;

                    info.methods.put(methodInfo.name + " " + methodInfo.descriptor, methodInfo);
                });
            }
            bodyDecl.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(ObjectCreationExpr expr, Void arg) {
                    expr.getAnonymousClassBody().ifPresent(nodes -> {
                        String anonFQN = fqn + "$" + anonymousClassCount.getAndIncrement();

                        visitBodyDeclarations(anonFQN, nodes, doc, symbolSolver, new AtomicInteger(1));

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
