package net.logstash.logback;

import java.io.OutputStream;
import java.io.Writer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public class JsonGeneratorFactory {
    private final GsonBuilder gsonBuilder;

    public JsonGeneratorFactory() {
        gsonBuilder = new GsonBuilder();
    }

    public GsonBuilder getGsonBuilder() {
        return gsonBuilder;
    }

    public JsonGenerator createGenerator(OutputStream output) {
        Gson gson = gsonBuilder.create();
        return new JsonGenerator(gson, output);
    }

    public JsonGenerator createGenerator(Writer writer) {
        Gson gson = gsonBuilder.create();
        return new JsonGenerator(gson, writer);
    }

    public JsonElement parse(String customFieldString) {
        return new JsonParser().parse(customFieldString);
    }
}
