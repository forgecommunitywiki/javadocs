package fcw.tasks;

import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.utils.SourceRoot;
import fcw.ParserUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class RemoveComments extends DefaultTask {
    @Input public File sourcesDir;
    @Input public Configuration configuration;
    @Input public boolean skipPackageInfo = true;

    @TaskAction
    public void act() throws IOException {
        SourceRoot sourceRoot = new SourceRoot(sourcesDir.toPath());
        Path docsRoot = sourcesDir.toPath().toAbsolutePath();

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

        sourceRoot.parseParallelized("", (localPath, absolutePath, result) -> {
            if (skipPackageInfo && localPath.getFileName().toString().endsWith("package-info.java")) {
                return SourceRoot.Callback.Result.DONT_SAVE;
            }
            result.ifSuccessful(compilationUnit -> compilationUnit.getAllComments().stream()
                .filter(Comment::isJavadocComment)
                .forEach(Comment::remove));
            return SourceRoot.Callback.Result.SAVE;
        });
    }
}
