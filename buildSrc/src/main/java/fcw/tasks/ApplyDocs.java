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
import com.github.javaparser.utils.CodeGenerationUtils;
import com.github.javaparser.utils.SourceRoot;
import com.github.javaparser.utils.SourceRoot.Callback.Result;
import fcw.info.DocInfo;
import fcw.DocUtils;
import fcw.IdentifyingVisitor;
import fcw.ParserUtils;
import fcw.info.PackageInfo;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static fcw.info.DocInfo.ClassInfo;
import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.FileVisitResult.SKIP_SUBTREE;

public class ApplyDocs extends DefaultTask {
    @Input public File docsDir;
    @Input public File sourcesDir;
    @Input public File pkgInfoTemplate;
    @Input public Configuration configuration;
    @Input public String docFileExtension = ".json";

    @TaskAction
    public void act() throws IOException {

        SourceRoot sourceRoot = new SourceRoot(sourcesDir.toPath());
        Path docsRoot = docsDir.toPath().toAbsolutePath();

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

        if (!Files.exists(docsRoot)) return;
        DirectoryTraverse traverse = new DirectoryTraverse(getLogger(), docsRoot, (dir) -> true, (absolutePath, attrs) -> {
            if (absolutePath.toString().endsWith(docFileExtension)) {
                try {
                    Path localPath = docsRoot.relativize(absolutePath);
                    String pkg = localPath.getParent() != null
                        ? localPath.getParent().toString().replace('/', '.')
                        : "";
                    String fileName = localPath.getFileName().toString().replaceFirst(docFileExtension, ".java");

                    Path docFile = CodeGenerationUtils.fileInPackageAbsolutePath(sourceRoot.getRoot(), pkg, fileName);

                    if (pkgInfoTemplate != null
                        && !pkg.isEmpty()
                        && fileName.endsWith("package-info.java")
                        && Files.notExists(docFile)
                        && pkgInfoTemplate.exists()) {
                        try (Stream<String> lines = Files.lines(pkgInfoTemplate.toPath())) {
                            Files.write(docFile, lines.map(str -> str.replace("${pkg}", pkg))
                                .collect(Collectors.toList()));
                        }
                    }

                    sourceRoot.parse(pkg, fileName, (local, absolute, result) -> {
                        final CompilationUnit cu = result.getResult().orElseThrow(() -> new IllegalStateException(
                            "Compilation error for file " + local + " under " + docsRoot + ": " + result.getProblems()));

                        if (fileName.endsWith("package-info.java")) {
                            PackageInfo info = PackageInfo.read(absolutePath);
                            if (!info.isEmpty()) {
                                cu.setComment(DocUtils.createComment(info.javadoc));
                                return Result.SAVE;
                            }

                            return Result.DONT_SAVE;
                        }

                        DocInfo doc = DocInfo.read(absolutePath);
                        if (doc.isEmpty()) return Result.DONT_SAVE;

                        ApplyDocsVisitor visitor = new ApplyDocsVisitor(symbolSolver, doc);
                        visitor.visit(cu);

                        return visitor.modified ? Result.SAVE : Result.DONT_SAVE;
                    });
                } catch (IOException e) {
                    getLogger().error("Exception while reading docs file " + absolutePath, e);
                }
            }
            return CONTINUE;
        });
        ForkJoinPool pool = new ForkJoinPool();
        pool.invoke(traverse);
    }

    private static class DirectoryTraverse extends RecursiveAction {
        private final Logger logger;
        private final Path path;
        private final Predicate<Path> dirChecker;
        private final VisitFileCallback callback;

        DirectoryTraverse(Logger logger, Path path, Predicate<Path> dirChecker, VisitFileCallback callback) {
            this.logger = logger;
            this.path = path;
            this.dirChecker = dirChecker;
            this.callback = callback;
        }

        @Override
        protected void compute() {
            final List<DirectoryTraverse> walks = new ArrayList<>();
            try {
                Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (!dirChecker.test(dir)) {
                            return SKIP_SUBTREE;
                        }
                        if (!dir.equals(DirectoryTraverse.this.path)) {
                            DirectoryTraverse w = new DirectoryTraverse(logger, dir, dirChecker, callback);
                            w.fork();
                            walks.add(w);
                            return SKIP_SUBTREE;
                        } else {
                            return CONTINUE;
                        }
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        return callback.process(file, attrs);
                    }
                });
            } catch (IOException e) {
                logger.error("Exception while parallel traversing directory", e);
            }

            for (DirectoryTraverse w : walks) {
                w.join();
            }
        }

        interface VisitFileCallback {
            FileVisitResult process(Path file, BasicFileAttributes attrs);
        }
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
