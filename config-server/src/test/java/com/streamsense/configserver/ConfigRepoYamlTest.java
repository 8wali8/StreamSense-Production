package com.streamsense.configserver;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Every file under config-repo must parse with duplicate keys rejected. The config server's own
 * loader refuses duplicates, and a file it cannot load turns into a 500 for every client that asks
 * for it at start-up; the failure only shows once the whole stack is running. This test moves it
 * into the config-server build, which runs whenever config-repo changes.
 */
class ConfigRepoYamlTest {

    private static final Path CONFIG_REPO = Path.of("config-repo");

    @TestFactory
    List<DynamicTest> everyConfigRepoFileParsesWithDuplicateKeysRejected() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(CONFIG_REPO, "*.{yml,yaml}")) {
            for (Path file : files) {
                tests.add(DynamicTest.dynamicTest(file.getFileName().toString(), () -> parseStrictly(file)));
            }
        }
        if (tests.isEmpty()) {
            throw new IllegalStateException("no config files found under " + CONFIG_REPO.toAbsolutePath());
        }
        return tests;
    }

    private static void parseStrictly(Path file) throws IOException {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Yaml yaml = new Yaml(new SafeConstructor(options));
        try (InputStream in = Files.newInputStream(file)) {
            assertThatCode(() -> yaml.loadAll(in).forEach(document -> { }))
                    .as("%s must parse with duplicate keys rejected", file)
                    .doesNotThrowAnyException();
        }
    }
}
