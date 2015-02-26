package net.logstash.logback;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonWriter;


public class JsonGenerator {
    private final Gson gson;
    private final Writer writer;
    private final JsonWriter jsonOutput;

    public JsonGenerator(Gson gson, OutputStream output) {
        this.gson = gson;
        writer = new OutputStreamWriter(output);
        jsonOutput = new JsonWriter(writer);
    }

    public JsonGenerator(Gson gson, Writer writer) {
        this.gson = gson;
        this.writer = writer;
        jsonOutput = new JsonWriter(writer);
    }

    public void writeFieldName(String name) throws IOException {
        jsonOutput.name(name);
    }

    public void writeObject(Object value) {
        gson.toJson(value, value.getClass(), jsonOutput);
    }

    public void writeStringField(String fieldName, String fieldValue) throws IOException {
        jsonOutput.name(fieldName);
        jsonOutput.value(fieldValue);
    }

    public void writeNumberField(String fieldName, int fieldValue) throws IOException {
        jsonOutput.name(fieldName);
        jsonOutput.value(fieldValue);
    }

    public void writeNumberField(String fieldName, long fieldValue) throws IOException {
        jsonOutput.name(fieldName);
        jsonOutput.value(fieldValue);
    }

    public void writeStartObject() throws IOException {
        jsonOutput.beginObject();
    }

    public void writeEndObject() throws IOException {
        jsonOutput.endObject();
    }

    public void flush() throws IOException {
        jsonOutput.flush();
    }

    public void writeObjectFieldStart(String fiedName) throws IOException {
        jsonOutput.name(fiedName);
        jsonOutput.beginObject();
    }

    public void writeTree(JsonElement value) {
        gson.toJson(value, jsonOutput);
    }

    public void writeEndArray() throws IOException {
        jsonOutput.endArray();
    }

    public void writeArrayFieldStart(String fieldName) throws IOException {
        jsonOutput.name(fieldName);
        jsonOutput.beginArray();
    }

    public void writeString(String value) throws IOException {
        jsonOutput.value(value);
    }

    public void writeRawValue(String rawJson) throws IOException {
        writer.append(rawJson);
    }
}
