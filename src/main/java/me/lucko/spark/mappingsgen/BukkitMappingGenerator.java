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
import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;

public class BukkitMappingGenerator {

    // https://hub.spigotmc.org/versions/
    private static final String version = "1.21";
    private static final String hash = "ae1e7b1e31cd3a3892bb05a6ccdcecc48c73c455";

    public static void main(String[] args) throws Exception {
        System.out.println("Reading mappings...");

        VisitableMappingTree tree = new MemoryMappingTree();
        try (HttpClient client = HttpClient.newHttpClient()) {
            String url = "https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/raw/mappings/bukkit-%s-cl.csrg?at=%s".formatted(version, hash);
            HttpResponse<InputStream> resp = client.send(
                    HttpRequest.newBuilder().uri(URI.create(url)).build(),
                    HttpResponse.BodyHandlers.ofInputStream()
            );

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resp.body()))) {
                reader.readLine(); // skip header
                MappingReader.read(reader, MappingFormat.CSRG_FILE, tree);
            }
        }

        Map<String, ClassMapping> mappings = new TreeMap<>();
        for (MappingTree.ClassMapping clazz : tree.getClasses()) {
            String srcName = clazz.getSrcName().replace('/', '.');
            String dstName = clazz.getDstName(0).replace('/', '.');

            mappings.put(dstName, new ClassMapping(srcName, dstName));
        }

        System.out.println("Writing output...");
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get("bukkit.json"))) {
            Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
            gson.toJson(ImmutableMap.of("classes", mappings), writer);
        }

        System.out.println("Done!");
    }

    public record ClassMapping(String obfuscated, String mapped) { }

}
