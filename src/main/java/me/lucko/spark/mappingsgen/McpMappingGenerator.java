package me.lucko.spark.mappingsgen;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.mappingio.MappingReader;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class McpMappingGenerator {
    private static final String version = "1.21";

    public static void main(String[] args) throws Exception {
        System.out.println("Loading mojang mapping...");
        VisitableMappingTree mojang = MojangMappingGenerator.readMappingsForVersion(version);

        System.out.println("Loading mcp mapping...");
        Path zipPath = readZip("https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/" + version + "/mcp_config-" + version + ".zip");

        VisitableMappingTree tree = new MemoryMappingTree();

        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            ZipEntry zipEntry = zip.stream().filter(e -> e.getName().equals("config/joined.tsrg")).findFirst().get();

            try (InputStream is = zip.getInputStream(zipEntry)) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                    MappingReader.read(reader, MappingFormat.TSRG_FILE, tree);
                }
            }
        }

        Files.delete(zipPath);

        Map<String, String> methods = new TreeMap<>();

        for (MappingTree.ClassMapping clazz : tree.getClasses()) {
            String obfClass = clazz.getSrcName();

            for (MappingTree.MethodMapping method : clazz.getMethods()) {
                String obfMethod = method.getSrcName();
                String obfDesc = method.getSrcDesc();
                String namedMethod = method.getDstName(0);

                if (namedMethod.equals(obfMethod)) {
                    continue;
                }

                MappingTree.MethodMapping mapping = mojang.getMethod(obfClass, obfMethod, obfDesc);
                if (mapping == null) {
                    System.err.println("MISSING:  " + obfClass + "#" + obfMethod + " -> " + namedMethod + " = ???");
                    continue;
                }

                String officialName = mapping.getDstName(0);

                if (namedMethod.equals(officialName)) {
                    continue;
                }
                if (namedMethod.startsWith("f_")) {
                    continue;
                }

                methods.put(namedMethod, officialName);
            }
        }

        System.out.println("Writing output...");
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get("mcp.json"))) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(ImmutableMap.of("methods", methods), writer);
        }

        System.out.println("Done!");
    }

    private static Path readZip(String url) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            Path tmpFile = Files.createTempFile("mcp_config", ".zip");
            client.send(
                    HttpRequest.newBuilder().uri(URI.create(url)).build(),
                    HttpResponse.BodyHandlers.ofFile(tmpFile)
            );
            return tmpFile;
        }
    }

}
