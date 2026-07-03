package io.oatg.cli;

import io.oatg.Main;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GenerateCommandIT {

    @TempDir
    Path tempDir;

    private int run(Path out, String... extra) {
        String[] base = {"generate",
                "--spec", "src/test/resources/specs/petstore.yaml",
                "--out", out.toString(),
                "--seed", "42"};
        String[] args = new String[base.length + extra.length];
        System.arraycopy(base, 0, args, 0, base.length);
        System.arraycopy(extra, 0, args, base.length, extra.length);
        return Main.buildCommandLine().execute(args);
    }

    @Test
    void generatesCollectionAndFeatures() throws IOException {
        int exit = run(tempDir);
        assertThat(exit).isZero();

        Path collection = tempDir.resolve("requests.json");
        assertThat(collection).exists();
        String json = Files.readString(collection);
        assertThat(json).contains("\"collectionVersion\" : 1");
        assertThat(json).contains("\"seed\" : 42");
        assertThat(json).contains("listPets").contains("addPet").contains("placeOrder");

        Path features = tempDir.resolve("features");
        assertThat(features.resolve("pets.feature")).exists();
        assertThat(features.resolve("store.feature")).exists();
        String pets = Files.readString(features.resolve("pets.feature"));
        assertThat(pets).contains("Feature: Petstore — pets");
        assertThat(pets).contains("@generated").contains("@seed-42").contains("@operation-addpet");
        assertThat(pets).contains("Then the response status should be one of 201, 400");
    }

    @Test
    void outputIsDeterministicForSameSeed() throws IOException {
        Path first = tempDir.resolve("a");
        Path second = tempDir.resolve("b");
        assertThat(run(first)).isZero();
        assertThat(run(second)).isZero();
        assertThat(Files.readString(second.resolve("requests.json")))
                .isEqualTo(Files.readString(first.resolve("requests.json")));
        assertThat(Files.readString(second.resolve("features/pets.feature")))
                .isEqualTo(Files.readString(first.resolve("features/pets.feature")));
    }

    @Test
    void pathFiltersLimitOperations() throws IOException {
        Path out = tempDir.resolve("filtered");
        int exit = run(out, "--include", "/store/**");
        assertThat(exit).isZero();
        String json = Files.readString(out.resolve("requests.json"));
        assertThat(json).contains("placeOrder");
        assertThat(json).doesNotContain("listPets");
    }

    @Test
    void missingSpecExitsWithCode2() {
        int exit = Main.buildCommandLine().execute("generate",
                "--spec", "does-not-exist.yaml", "--out", tempDir.resolve("x").toString());
        assertThat(exit).isEqualTo(2);
    }
}
