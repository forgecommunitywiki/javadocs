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
        sourceRoot.setPrinter(ParserUtils.PRINTER::print);

        sourceRoot.parseParallelized((local, absolute, result) -> {
            final CompilationUnit cu = result.getResult().orElseThrow(() -> new IllegalStateException(
                "Compilation error for file " + local + " under " + docsRoot + ": " + result.getProblems()));

            DocInfo doc = new DocInfo();

            if (cu.getAllComments().isEmpty()) return SourceRoot.Callback.Result.DONT_SAVE;

            final MakeDocsVisitor visitor = new MakeDocsVisitor(symbolSolver, doc);
            visitor.visit(cu);

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

    static class MakeDocsVisitor extends IdentifyingVisitor {
        private final DocInfo doc;

        public MakeDocsVisitor(SymbolResolver resolver, DocInfo doc) {
            super(resolver);
            this.doc = doc;
        }

        @Override
        protected void visitClass(TypeDeclaration<?> n, VisitContext ctx) {
            n.getJavadocComment().map(DocUtils::parseComment).ifPresent(javadoc -> {
                ClassInfo classInfo = new ClassInfo(ctx.getQualifiedName());
                classInfo.javadoc = javadoc;
                doc.classes.put(classInfo.name, classInfo);
            });
        }

        @Override
        protected void visitEnumConstant(EnumConstantDeclaration n, VisitContext ctx) {
            n.getJavadocComment().map(DocUtils::parseComment).ifPresent(javadoc -> {
                ClassInfo info = doc.classes.computeIfAbsent(ctx.getQualifiedName(), ClassInfo::new);

                ClassInfo.FieldInfo fieldInfo = new ClassInfo.FieldInfo(n.resolve().getName());
                fieldInfo.javadoc = javadoc;

                info.fields.put(fieldInfo.name, fieldInfo);
            });
        }

        @Override
        protected void visitField(FieldDeclaration n, VisitContext ctx) {
            n.getJavadocComment().map(DocUtils::parseComment).ifPresent(javadoc -> {
                ClassInfo info = doc.classes.computeIfAbsent(ctx.getQualifiedName(), ClassInfo::new);

                ClassInfo.FieldInfo fieldInfo = new ClassInfo.FieldInfo(n.resolve().getName());
                fieldInfo.javadoc = javadoc;

                info.fields.put(fieldInfo.name, fieldInfo);
            });
        }

        @Override
        protected void visitMethod(MethodDeclaration n, String descriptor, VisitContext ctx) {
            n.getJavadocComment().map(DocUtils::parseComment).ifPresent(javadoc -> {
                ClassInfo info = doc.classes.computeIfAbsent(ctx.getQualifiedName(), ClassInfo::new);

                ClassInfo.MethodInfo methodInfo = new ClassInfo.MethodInfo(n.getNameAsString(), descriptor);
                methodInfo.javadoc = javadoc;

                info.methods.put(methodInfo.name + " " + methodInfo.descriptor, methodInfo);
            });
        }

        @Override
        protected void visitConstructor(ConstructorDeclaration n, String descriptor, VisitContext ctx) {
            n.getJavadocComment().map(DocUtils::parseComment).ifPresent(javadoc -> {
                ClassInfo info = doc.classes.computeIfAbsent(ctx.getQualifiedName(), ClassInfo::new);

                ClassInfo.MethodInfo methodInfo = new ClassInfo.MethodInfo(n.getNameAsString(), descriptor);
                methodInfo.javadoc = javadoc;

                info.methods.put(methodInfo.name + " " + methodInfo.descriptor, methodInfo);
            });
        }

        @Override
        protected void visitAnnotationMember(AnnotationMemberDeclaration n, String descriptor, VisitContext ctx) {
            n.getJavadocComment().map(DocUtils::parseComment).ifPresent(javadoc -> {
                ClassInfo info = doc.classes.computeIfAbsent(ctx.getQualifiedName(), ClassInfo::new);

                ClassInfo.MethodInfo methodInfo = new ClassInfo.MethodInfo(n.getNameAsString(), descriptor);
                methodInfo.javadoc = javadoc;

                info.methods.put(methodInfo.name + " " + methodInfo.descriptor, methodInfo);
            });
        }
    }
}
