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

            boolean modified = false;

            for (TypeDeclaration<?> type : cu.getTypes()) {
                final String fqn = ParserUtils.toFQN(type.resolve());

                ClassInfo clsInfo = doc.classes.get(fqn);
                if (clsInfo == null) continue;
                if (clsInfo.javadoc != null) {
                    type.setComment(DocUtils.createComment(clsInfo.javadoc));
                    modified = true;
                }

                if (!clsInfo.fields.isEmpty()) {
                    for (FieldDeclaration field : type.getFields()) {
                        ClassInfo.FieldInfo fieldInfo = clsInfo.fields.get(field.resolve().getName());
                        if (fieldInfo != null && fieldInfo.javadoc != null) {
                            type.setComment(DocUtils.createComment(fieldInfo.javadoc));
                            modified = true;
                        }
                    }

                    if (type instanceof EnumDeclaration) {
                        EnumDeclaration enumType = (EnumDeclaration) type;
                        for (EnumConstantDeclaration enumConstant : enumType.getEntries()) {
                            ClassInfo.FieldInfo fieldInfo = clsInfo.fields.get(enumConstant.resolve().getName());
                            if (fieldInfo != null && fieldInfo.javadoc != null) {
                                type.setComment(DocUtils.createComment(fieldInfo.javadoc));
                                modified = true;
                            }
                        }
                    }
                }

                if (!clsInfo.methods.isEmpty()) {
                    for (MethodDeclaration method : type.getMethods()) {
                        String key = method.getNameAsString() + " " + ParserUtils.toDescriptor(symbolSolver, method);
                        ClassInfo.MethodInfo methodInfo = clsInfo.methods.get(key);
                        if (methodInfo != null && methodInfo.javadoc != null) {
                            type.setComment(DocUtils.createComment(methodInfo.javadoc));
                            modified = true;
                        }
                    }

                    for (ConstructorDeclaration constructor : type.getConstructors()) {
                        String key = constructor.getNameAsString() + " " + ParserUtils.toDescriptor(symbolSolver, constructor);
                        ClassInfo.MethodInfo ctorInfo = clsInfo.methods.get(key);
                        if (ctorInfo != null && ctorInfo.javadoc != null) {
                            type.setComment(DocUtils.createComment(ctorInfo.javadoc));
                            modified = true;
                        }
                    }
                }
            }

            return modified ? Result.SAVE : Result.DONT_SAVE;
        });
    }
}
