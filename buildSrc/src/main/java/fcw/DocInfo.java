package fcw;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.github.javaparser.javadoc.Javadoc;
import com.github.javaparser.javadoc.JavadocBlockTag;
import com.github.javaparser.javadoc.description.JavadocDescription;
import com.github.javaparser.utils.LineSeparator;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;

public class DocInfo {
    private static final ObjectMapper JSON = new ObjectMapper();

    static {
        JSON.enable(SerializationFeature.INDENT_OUTPUT);
        DefaultIndenter indent = new DefaultIndenter("    ", LineSeparator.LF.toString());
        JSON.setDefaultPrettyPrinter(new DefaultPrettyPrinter().withObjectIndenter(indent).withArrayIndenter(indent));
        JSON.registerModule(
            new SimpleModule()
                .addSerializer(DocInfo.class, new DocInfo.JacksonSerializer())
                .addDeserializer(DocInfo.class, new DocInfo.JacksonDeserializer())
        );
    }

    public static DocInfo read(Path path) {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            return JSON.readValue(reader, DocInfo.class);
        } catch (IOException e) {
            throw new RuntimeException("Unable to read doc file from " + path, e);
        }
    }

    // class fqn (<package>.class[$inner_class]*)
    // All classes, both the outer and all inners, exist here
    public final Map<String, ClassInfo> classes = new LinkedHashMap<>();

    public void write(Path out) {
        try (BufferedWriter writer = Files.newBufferedWriter(out, StandardOpenOption.CREATE_NEW)) {
            JSON.writeValue(writer, this);
        } catch (IOException e) {
            throw new RuntimeException("Unable to write doc file at " + out, e);
        }
    }

    public static class ClassInfo {
        public final String name;
        public Javadoc javadoc = null;
        // "<method name> <method descriptor>" method
        public final Map<String, MethodInfo> methods = new LinkedHashMap<>();
        // field name, field
        public final Map<String, FieldInfo> fields = new LinkedHashMap<>();

        public ClassInfo(String name) {
            this.name = name;
        }

        public static class MethodInfo {
            public final String name;
            public final String descriptor;
            public Javadoc javadoc = null;

            public MethodInfo(String name, String descriptor) {
                this.name = name;
                this.descriptor = descriptor;
            }
        }

        public static class FieldInfo {
            public final String name;
            public Javadoc javadoc = null;

            public FieldInfo(String name) {
                this.name = name;
            }
        }

    }

    public static class JacksonSerializer extends StdSerializer<DocInfo> {
        public JacksonSerializer() {
            super(DocInfo.class);
        }

        @Override
        public void serialize(DocInfo value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartArray();
            for (ClassInfo clsInfo : value.classes.values()) {
                gen.writeStartObject();
                gen.writeStringField("name", clsInfo.name);
                writeJavadoc(gen, clsInfo.javadoc);

                if (!clsInfo.fields.isEmpty()) {
                    gen.writeArrayFieldStart("fields");
                    for (ClassInfo.FieldInfo fieldInfo : clsInfo.fields.values()) {
                        gen.writeStartObject();

                        gen.writeStringField("name", fieldInfo.name);
                        writeJavadoc(gen, fieldInfo.javadoc);

                        gen.writeEndObject();
                    }
                    gen.writeEndArray();
                }

                if (!clsInfo.methods.isEmpty()) {
                    gen.writeArrayFieldStart("methods");
                    for (ClassInfo.MethodInfo methInfo : clsInfo.methods.values()) {
                        gen.writeStartObject();

                        gen.writeStringField("name", methInfo.name);
                        gen.writeStringField("descriptor", methInfo.descriptor);
                        writeJavadoc(gen, methInfo.javadoc);

                        gen.writeEndObject();
                    }
                    gen.writeEndArray();
                }
                gen.writeEndObject();
            }
            gen.writeEndArray();
        }

        private void writeJavadoc(JsonGenerator gen, Javadoc javadoc) throws IOException {
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
    }

    public static class JacksonDeserializer extends StdDeserializer<DocInfo> {
        protected JacksonDeserializer() {
            super(DocInfo.class);
        }

        @Override
        public DocInfo deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            final JsonNode baseNode = ctxt.readTree(p);
            final DocInfo info = new DocInfo();
            for (JsonNode node : baseNode) {
                final String name = node.get("name").asText();
                final ClassInfo clsInfo = new ClassInfo(name);
                clsInfo.javadoc = readJavadoc(node);

                for (JsonNode fieldNode : node.path("fields")) {
                    final String fieldName = fieldNode.get("name").asText();
                    final ClassInfo.FieldInfo fieldInfo = new ClassInfo.FieldInfo(fieldName);

                    fieldInfo.javadoc = readJavadoc(fieldNode);
                    clsInfo.fields.put(fieldInfo.name, fieldInfo);
                }

                for (JsonNode methodNode : node.path("methods")) {
                    final String methodName = methodNode.get("name").asText();
                    final String methodDescriptor = methodNode.get("descriptor").asText();
                    final ClassInfo.MethodInfo methodInfo = new ClassInfo.MethodInfo(methodName, methodDescriptor);

                    methodInfo.javadoc = readJavadoc(methodNode);
                    clsInfo.methods.put(methodInfo.name + " " + methodInfo.descriptor, methodInfo);
                }

                info.classes.put(clsInfo.name, clsInfo);
            }
            return info;
        }

        @Nullable
        private Javadoc readJavadoc(JsonNode node) {
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
}
