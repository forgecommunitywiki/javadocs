package fcw;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.javadoc.Javadoc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

public class DocUtils {
    public static JavadocComment createComment(Javadoc doc) {
        Javadoc javadoc = new Javadoc(doc.getDescription());
        doc.getBlockTags().forEach(javadoc::addBlockTag);
        return javadoc.toComment();
    }

    @Nullable
    public static Javadoc parseComment(JavadocComment comment) {
        String content = comment.getContent();
        if (content.equals("empty")) return null;
        final Javadoc javadoc = StaticJavaParser.parseJavadoc(content);
        if (javadoc.getDescription().isEmpty() && javadoc.getBlockTags().isEmpty()) {
            return null;
        }
        return javadoc;
    }

    public static List<File> filter(File sourceDir, File docsDir, String docFileExtension) {
        Path sourcePath = sourceDir.toPath();
        Path docsPath = docsDir.toPath();
        try (Stream<Path> paths = Files.walk(sourcePath)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(srcFile -> Files.exists(
                    docsPath.resolve(sourcePath.relativize(srcFile).toString().replace(".java", docFileExtension))
                ))
                .map(Path::toFile)
                .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException("Failed to filter undocumented classes from javadoc generation", e);
        }
    }
}
