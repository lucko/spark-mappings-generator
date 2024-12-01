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
import java.util.zip.GZIPInputStream;

public class YarnMappingGenerator {

    private static final String build = "1.21+build.9";

    public static void main(String[] args) throws Exception {
        System.out.println("Loading mapping...");

        Map<String, String> classes = new TreeMap<>();
        Map<String, String> methods = new TreeMap<>();

        VisitableMappingTree tree = readYarn();

        for (MappingTree.ClassMapping clazz : tree.getClasses()) {
            String intermediaryClass = clazz.getDstName(0).replace("/", ".");
            String namedClass = clazz.getDstName(1).replace("/", ".");

            if (intermediaryClass.equals(namedClass)) {
                continue;
            }
            if (intermediaryClass.matches(".+\\$[0-9]+")) {
                continue;
            }
            classes.put(intermediaryClass, namedClass);

            for (MappingTree.MethodMapping method : clazz.getMethods()) {
                String intermediaryMethod = method.getDstName(0);
                String namedMethod = method.getDstName(1);

                if (intermediaryMethod.equals(namedMethod)) {
                    continue;
                }
                methods.put(intermediaryMethod, namedMethod);
            }
        }

        System.out.println("Writing output...");
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get("yarn.json"))) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(ImmutableMap.of("classes", classes, "methods", methods), writer);
        }

        System.out.println("Done!");
    }

    private static VisitableMappingTree readYarn() throws Exception {
        VisitableMappingTree tree = new MemoryMappingTree();

        String url = "https://maven.fabricmc.net/net/fabricmc/yarn/" + build + "/yarn-" + build + "-tiny.gz";

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<InputStream> resp = client.send(
                    HttpRequest.newBuilder().uri(URI.create(url)).build(),
                    HttpResponse.BodyHandlers.ofInputStream()
            );

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(resp.body())))) {
                MappingReader.read(reader, MappingFormat.TINY_FILE, tree);
            }
        }

        return tree;
    }

}
