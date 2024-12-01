package me.lucko.spark.mappingsgen;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.mappingio.tree.VisitableMappingTree;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class MojangMappingGenerator {
    private static final String version = "1.21";

    public static void main(String[] args) throws Exception {
        System.out.println("Loading mapping...");

        VisitableMappingTree tree = readMappingsForVersion(version);
        Map<String, ClassMapping> classes = new TreeMap<>();

        for (MappingTree.ClassMapping clazz : tree.getClasses()) {
            String obfClass = clazz.getSrcName().replace("/", ".");
            String namedClass = clazz.getDstName(0).replace("/", ".");

            ClassMapping mapping = new ClassMapping(obfClass, namedClass);

            for (MappingTree.MethodMapping method : clazz.getMethods()) {
                String obfMethod = method.getSrcName();
                String namedMethod = method.getDstName(0);
                String obfDesc = method.getSrcDesc();

                if (namedMethod.equals(obfMethod)) {
                    continue;
                }

                mapping.addMethod(obfMethod, namedMethod, obfDesc);
            }

            if (obfClass.equals(namedClass) && mapping.methods.isEmpty()) {
                continue;
            }

            classes.put(obfClass, mapping);
        }

        System.out.println("Writing output...");
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get("mojang.json"))) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(ImmutableMap.of("classes", classes), writer);
        }

        System.out.println("Done!");
    }

    private static JsonObject readManifest(String url) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> resp = client.send(
                    HttpRequest.newBuilder().uri(URI.create(url)).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            return new Gson().fromJson(resp.body(), JsonObject.class);
        }
    }

    private static VisitableMappingTree readMappings(String url) throws Exception {
        VisitableMappingTree tree = new MemoryMappingTree();

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<InputStream> resp = client.send(
                    HttpRequest.newBuilder().uri(URI.create(url)).build(),
                    HttpResponse.BodyHandlers.ofInputStream()
            );

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resp.body()))) {
                MappingReader.read(reader, MappingFormat.PROGUARD_FILE, new MappingSourceNsSwitch(tree, "target"));
            }
        }

        return tree;
    }

    public static VisitableMappingTree readMappingsForVersion(String version) throws Exception {
        String versionUrl = null;

        JsonObject manifest = readManifest("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
        for (JsonElement versionElement : manifest.get("versions").getAsJsonArray()) {
            JsonObject versionObject = versionElement.getAsJsonObject();
            if (versionObject.get("id").getAsString().equals(version)) {
                versionUrl = versionObject.get("url").getAsString();
                break;
            }
        }

        JsonObject versionManifest = readManifest(versionUrl);

        String serverMappingsUrl = versionManifest.get("downloads").getAsJsonObject().get("server_mappings").getAsJsonObject().get("url").getAsString();
        String clientMappingsUrl = versionManifest.get("downloads").getAsJsonObject().get("client_mappings").getAsJsonObject().get("url").getAsString();

        VisitableMappingTree tree = readMappings(serverMappingsUrl);
        VisitableMappingTree clientTree = readMappings(clientMappingsUrl);

        for (MappingTree.ClassMapping clazz : clientTree.getClasses()) {
            tree.addClass(clazz);
        }

        return tree;
    }

    public static class ClassMapping {
        private final String obfuscated;
        private final String mapped;
        private final List<MethodMapping> methods = new ArrayList<>();

        public ClassMapping(String obfuscated, String mapped) {
            this.obfuscated = obfuscated;
            this.mapped = mapped;
        }

        public void addMethod(String obfuscated, String mojangName, String description) {
            this.methods.add(new MethodMapping(obfuscated, mojangName, description));
        }
    }

    private record MethodMapping(String obfuscated, String mojangName, String description) { }
}
