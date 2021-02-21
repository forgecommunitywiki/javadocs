package fcw.tasks;

import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.utils.SourceRoot;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;

public class RemoveComments extends DefaultTask {
    @Input public File directory;

    @TaskAction
    public void act() throws IOException {
        SourceRoot sourceRoot = new SourceRoot(directory.toPath());

        sourceRoot.parseParallelized("", (localPath, absolutePath, result) -> {
            result.ifSuccessful(compilationUnit -> compilationUnit.getAllComments().forEach(Comment::remove));
            return SourceRoot.Callback.Result.SAVE;
        });
    }
}
