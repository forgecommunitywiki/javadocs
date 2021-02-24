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

import static fcw.info.InfoUtils.*;

public class PackageInfo implements InfoHolder {
    public static PackageInfo read(Path path) {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            return JSON.readValue(reader, PackageInfo.class);
        } catch (IOException e) {
            throw new RuntimeException("Unable to read package info file from " + path, e);
        }
    }

    public final Javadoc javadoc;

    public PackageInfo(Javadoc javadoc) {
        this.javadoc = javadoc;
    }

    public boolean isEmpty() {
        return javadoc == null;
    }

    public void write(Path out) {
        try (BufferedWriter writer = Files.newBufferedWriter(out, StandardOpenOption.CREATE_NEW)) {
            JSON.writeValue(writer, this);
        } catch (IOException e) {
            throw new RuntimeException("Unable to write package info file at " + out, e);
        }
    }

    public static class Serializer extends StdSerializer<PackageInfo> {
        protected Serializer() {
            super(PackageInfo.class);
        }

        @Override
        public void serialize(PackageInfo value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartObject();
            writeJavadoc(gen, value.javadoc);
            gen.writeEndObject();
        }
    }

    public static class Deserializer extends StdDeserializer<PackageInfo> {

        protected Deserializer() {
            super(PackageInfo.class);
        }

        @Override
        public PackageInfo deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            final JsonNode node = ctxt.readTree(p);
            return new PackageInfo(readJavadoc(node));
        }
    }
}
