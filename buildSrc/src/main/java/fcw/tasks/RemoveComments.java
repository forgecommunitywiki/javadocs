package fcw.tasks;

import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.utils.SourceRoot;
import fcw.ParserUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;

public class RemoveComments extends DefaultTask {
    @Input public File directory;
    @Input public boolean skipPackageInfo = true;

    @TaskAction
    public void act() throws IOException {
        SourceRoot sourceRoot = new SourceRoot(directory.toPath());

        sourceRoot.getParserConfiguration().setAttributeComments(true);
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
