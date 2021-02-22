package fcw.tasks;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
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

            for (TypeDeclaration<?> type : cu.getTypes()) {
                final String fqn = ParserUtils.toFQN(type.resolve());

                type.getJavadocComment().map(DocUtils::parseComment).ifPresent(javadoc -> {
                    ClassInfo classInfo = new ClassInfo(fqn);
                    classInfo.javadoc = javadoc;
                    doc.classes.put(fqn, classInfo);
                });

                for (FieldDeclaration field : type.getFields()) {
                    field.getJavadocComment().map(DocUtils::parseComment).ifPresent(javadoc -> {
                        ClassInfo info = doc.classes.computeIfAbsent(fqn, ClassInfo::new);
                        ClassInfo.FieldInfo fieldInfo = new ClassInfo.FieldInfo(field.resolve().getName());
                        fieldInfo.javadoc = javadoc;
                        info.fields.put(fieldInfo.name, fieldInfo);
                    });
                }

                if (type instanceof EnumDeclaration) {
                    for (EnumConstantDeclaration enumConstant : ((EnumDeclaration) type).getEntries()) {
                        enumConstant.getJavadocComment().map(DocUtils::parseComment).ifPresent(javadoc -> {
                            ClassInfo info = doc.classes.computeIfAbsent(fqn, ClassInfo::new);
                            ClassInfo.FieldInfo fieldInfo = new ClassInfo.FieldInfo(enumConstant.resolve().getName());
                            fieldInfo.javadoc = javadoc;
                            info.fields.put(fieldInfo.name, fieldInfo);
                        });
                    }
                }

                for (MethodDeclaration method : type.getMethods()) {
                    method.getJavadocComment().map(DocUtils::parseComment).ifPresent(javadoc -> {
                        String key = method.getNameAsString() + " " + ParserUtils.toDescriptor(symbolSolver, method);
                        ClassInfo info = doc.classes.computeIfAbsent(fqn, ClassInfo::new);
                        ClassInfo.MethodInfo methodInfo = new ClassInfo.MethodInfo(
                            method.getNameAsString(), ParserUtils.toDescriptor(symbolSolver, method));
                        methodInfo.javadoc = javadoc;
                        info.methods.put(methodInfo.name + " " + methodInfo.descriptor, methodInfo);
                    });
                }

                for (ConstructorDeclaration constructor : type.getConstructors()) {
                    constructor.getJavadocComment().map(DocUtils::parseComment).ifPresent(javadoc -> {
                        String key = constructor.getNameAsString() + " " + ParserUtils.toDescriptor(symbolSolver, constructor);
                        ClassInfo info = doc.classes.computeIfAbsent(fqn, ClassInfo::new);
                        ClassInfo.MethodInfo methodInfo = new ClassInfo.MethodInfo(
                            constructor.getNameAsString(), ParserUtils.toDescriptor(symbolSolver, constructor));
                        methodInfo.javadoc = javadoc;
                        info.methods.put(methodInfo.name + " " + methodInfo.descriptor, methodInfo);
                    });
                }
            }

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
