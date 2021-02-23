package fcw.info;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.github.javaparser.javadoc.Javadoc;
import com.github.javaparser.javadoc.JavadocBlockTag;
import com.github.javaparser.javadoc.description.JavadocDescription;
import com.github.javaparser.utils.LineSeparator;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import javax.annotation.Nullable;

class InfoUtils {
    public static final ObjectMapper JSON = new ObjectMapper();

    static {
        JSON.enable(SerializationFeature.INDENT_OUTPUT);
        DefaultIndenter indent = new DefaultIndenter("    ", LineSeparator.LF.toString());
        JSON.setDefaultPrettyPrinter(new DefaultPrettyPrinter().withObjectIndenter(indent).withArrayIndenter(indent));
        JSON.registerModule(
            new SimpleModule()
                .addSerializer(DocInfo.class, new DocInfo.Serializer())
                .addSerializer(PackageInfo.class, new PackageInfo.Serializer())
                .addDeserializer(DocInfo.class, new DocInfo.Deserializer())
                .addDeserializer(PackageInfo.class, new PackageInfo.Deserializer())
        );
    }

    public static void writeJavadoc(JsonGenerator gen, Javadoc javadoc) throws IOException {
        if (javadoc != null && (!javadoc.getDescription().isEmpty() || !javadoc.getBlockTags().isEmpty())) {
            gen.writeObjectFieldStart("javadoc");

            if (!javadoc.getDescription().isEmpty()) {
                gen.writeArrayFieldStart("description");
                for (String line : javadoc.getDescription().toText().split("\n")) {
                    gen.writeString(line);
                }
                gen.writeEndArray();
            }

            if (!javadoc.getBlockTags().isEmpty()) {
                gen.writeObjectFieldStart("tags");
                for (JavadocBlockTag tag : javadoc.getBlockTags()) {
                    String text = tag.getContent().toText();
                    String content = tag.getName()
                        .map(str -> str + " " + text)
                        .orElse(text);
                    if (content.indexOf('\n') == -1) {
                        gen.writeStringField(tag.getTagName(), content);
                    } else {
                        gen.writeArrayFieldStart(tag.getTagName());
                        for (String line : content.split("\n")) {
                            gen.writeString(line);
                        }
                        gen.writeEndArray();
                    }
                }
                gen.writeEndObject();
            }

            gen.writeEndObject();
        }
    }

    @Nullable
    public static Javadoc readJavadoc(JsonNode node) {
        if (node.has("javadoc")) {
            JsonNode javadocNode = node.get("javadoc");
            Javadoc javadoc;

            if (javadocNode.has("description")) {
                StringBuilder builder = new StringBuilder();
                for (JsonNode element : javadocNode.get("description")) {
                    if (builder.length() != 0) builder.append('\n');
                    builder.append(element.asText());
                }
                javadoc = new Javadoc(JavadocDescription.parseText(builder.toString()));
            } else {
                javadoc = new Javadoc(new JavadocDescription());
            }

            if (javadocNode.has("tags")) {
                for (Iterator<Map.Entry<String, JsonNode>> it = javadocNode.get("tags").fields(); it.hasNext(); ) {
                    Map.Entry<String, JsonNode> entry = it.next();
                    if (entry.getValue().isArray()) {
                        StringBuilder builder = new StringBuilder();
                        for (JsonNode element : entry.getValue()) {
                            if (builder.length() != 0) builder.append('\n');
                            builder.append(element.asText());
                        }
                        javadoc.addBlockTag(entry.getKey(), builder.toString());
                    } else {
                        javadoc.addBlockTag(entry.getKey(), entry.getValue().asText());
                    }
                }
            }

            return javadoc;
        }
        return null;
    }
}
