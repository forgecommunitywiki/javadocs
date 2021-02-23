package fcw.info;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.github.javaparser.javadoc.Javadoc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

import static fcw.info.InfoUtils.*;

public class DocInfo implements InfoHolder {
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

    public boolean isEmpty() {
        return classes.isEmpty();
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

    public static class Serializer extends StdSerializer<DocInfo> {
        public Serializer() {
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
    }

    public static class Deserializer extends StdDeserializer<DocInfo> {
        protected Deserializer() {
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
    }

}
