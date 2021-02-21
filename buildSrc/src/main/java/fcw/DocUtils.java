package fcw;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.javadoc.Javadoc;

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
}
