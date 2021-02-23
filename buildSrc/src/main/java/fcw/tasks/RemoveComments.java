package fcw.tasks;

import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.utils.SourceRoot;
import fcw.ParserUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

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
            return result.getResult()
                .map(compilationUnit -> {
                    final List<Comment> comments = compilationUnit.getAllComments().stream()
                        .filter(Comment::isJavadocComment)
                        .collect(Collectors.toList());
                    if (!comments.isEmpty()) {
                        comments.forEach(Comment::remove);
                        return SourceRoot.Callback.Result.SAVE;
                    }
                    return SourceRoot.Callback.Result.DONT_SAVE;
                }).orElse(SourceRoot.Callback.Result.DONT_SAVE);
        });
    }
}
