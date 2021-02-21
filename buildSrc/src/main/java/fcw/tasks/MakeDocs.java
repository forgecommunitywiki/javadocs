package fcw.tasks;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.javadoc.Javadoc;
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

            cu.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(ClassOrInterfaceDeclaration n, Void arg) {
                    n.getJavadocComment().ifPresent(comment -> {
                        Javadoc javadoc = DocUtils.parseComment(comment);
                        if (javadoc != null) {
                            String fqn = ParserUtils.toFQN(n);

                            ClassInfo classInfo = new ClassInfo(fqn);
                            classInfo.javadoc = javadoc;
                            ClassInfo old = doc.classes.put(fqn, classInfo);
                            if (old != null) {
                                throw new IllegalStateException(
                                    "Class " + fqn + " already had an entry before reaching it");
                            }
                        }
                    });
                    super.visit(n, arg);
                }

                @Override
                public void visit(EnumDeclaration n, Void arg) {
                    n.getJavadocComment().ifPresent(comment -> {
                        Javadoc javadoc = DocUtils.parseComment(comment);
                        if (javadoc != null) {
                            String fqn = ParserUtils.toFQN(n.resolve());

                            ClassInfo classInfo = new ClassInfo(fqn);
                            classInfo.javadoc = javadoc;
                            ClassInfo old = doc.classes.put(fqn, classInfo);
                            if (old != null) {
                                throw new IllegalStateException(
                                    "Class " + fqn + " already had an entry before reaching it");
                            }
                        }
                    });
                    super.visit(n, arg);
                }

                @Override
                public void visit(FieldDeclaration n, Void arg) {
                    n.getJavadocComment().ifPresent(comment -> {
                        Javadoc javadoc = DocUtils.parseComment(comment);
                        if (javadoc != null) {
                            final TypeDeclaration<?> classDeclaration = n.getParentNode()
                                .filter(TypeDeclaration.class::isInstance)
                                .map(TypeDeclaration.class::cast)
                                .orElse(null);
                            if (classDeclaration == null) return;
                            // TODO: fields can be in anonymous classes.

                            String fqn = ParserUtils.toFQN(classDeclaration.resolve());

                            ClassInfo info = doc.classes.computeIfAbsent(fqn, ClassInfo::new);
                            ClassInfo.FieldInfo field = new ClassInfo.FieldInfo(n.resolve().getName());
                            field.javadoc = javadoc;

                            ClassInfo.FieldInfo old = info.fields.put(field.name, field);
                            if (old != null) {
                                throw new IllegalStateException(
                                    "Field " + field + " already had an entry before reaching it");
                            }
                        }
                    });
                    super.visit(n, arg);
                }

                @Override
                public void visit(EnumConstantDeclaration n, Void arg) {
                    n.getJavadocComment().ifPresent(comment -> {
                        Javadoc javadoc = DocUtils.parseComment(comment);
                        if (javadoc != null) {
                            final EnumDeclaration classDeclaration = n.getParentNode()
                                .filter(EnumDeclaration.class::isInstance)
                                .map(EnumDeclaration.class::cast)
                                .orElseThrow(() -> new IllegalStateException("Enum constant does not have a parent class"));

                            String fqn = ParserUtils.toFQN(classDeclaration.resolve());

                            ClassInfo info = doc.classes.computeIfAbsent(fqn, ClassInfo::new);
                            ClassInfo.FieldInfo field = new ClassInfo.FieldInfo(n.resolve().getName());
                            field.javadoc = javadoc;

                            ClassInfo.FieldInfo old = info.fields.put(field.name, field);
                            if (old != null) {
                                throw new IllegalStateException(
                                    "Enum constant " + field + " already had an entry before reaching it");
                            }
                        }
                    });
                    super.visit(n, arg);
                }

                @Override
                public void visit(MethodDeclaration n, Void arg) {
                    n.getJavadocComment().ifPresent(comment -> {
                        Javadoc javadoc = DocUtils.parseComment(comment);
                        if (javadoc != null) {
                            final TypeDeclaration<?> classDeclaration = n.getParentNode()
                                .filter(TypeDeclaration.class::isInstance)
                                .map(TypeDeclaration.class::cast)
                                .orElse(null);
                            if (classDeclaration == null) return;
                            // TODO: methods can be in anonymous classes.

                            String fqn = ParserUtils.toFQN(classDeclaration.resolve());

                            ClassInfo info = doc.classes.computeIfAbsent(fqn, ClassInfo::new);
                            ClassInfo.MethodInfo method = new ClassInfo.MethodInfo(n.getNameAsString(),
                                ParserUtils.toDescriptor(symbolSolver, n));
                            method.javadoc = javadoc;

                            ClassInfo.MethodInfo old = info.methods.put(method.name + " " + method.descriptor, method);
                            if (old != null) {
                                throw new IllegalStateException(
                                    "Method " + method + " already had an entry before reaching it");
                            }
                        }
                    });
                    super.visit(n, arg);
                }

                @Override
                public void visit(ConstructorDeclaration n, Void arg) {
                    n.getJavadocComment().ifPresent(comment -> {
                        Javadoc javadoc = DocUtils.parseComment(comment);
                        if (javadoc != null) {
                            final TypeDeclaration<?> classDeclaration = n.getParentNode()
                                .filter(TypeDeclaration.class::isInstance)
                                .map(TypeDeclaration.class::cast)
                                .orElse(null);
                            if (classDeclaration == null) return;
                            // TODO: methods can be in anonymous classes.

                            String fqn = ParserUtils.toFQN(classDeclaration.resolve());

                            ClassInfo info = doc.classes.computeIfAbsent(fqn, ClassInfo::new);
                            ClassInfo.MethodInfo method = new ClassInfo.MethodInfo(n.getNameAsString(),
                                ParserUtils.toDescriptor(symbolSolver, n));
                            method.javadoc = javadoc;

                            ClassInfo.MethodInfo old = info.methods.put(method.name + " " + method.descriptor, method);
                            if (old != null) {
                                throw new IllegalStateException(
                                    "Method " + method + " already had an entry before reaching it");
                            }
                        }
                    });
                    super.visit(n, arg);
                }
            }, null);

            if (doc.classes.isEmpty()) return SourceRoot.Callback.Result.DONT_SAVE;

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

            return SourceRoot.Callback.Result.DONT_SAVE;
        });
    }
}
