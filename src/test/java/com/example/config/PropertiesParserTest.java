package com.example.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.config.core.ConfigLoader;
import com.example.config.exception.ConfigParseException;
import com.example.config.parse.PropertiesParser;

class PropertiesParserTest {

    @Test
    void parsesCommentsBlankLinesMultipleSeparatorsAndContinuations() {
        String content = String.join("\n",
                "# 全行注释",
                "! 另一种注释",
                "",
                "first = one",
                "second: two",
                "third   three",
                "continued = a\\",
                "b\\",
                "c",
                "escaped=hello\\nworld\\=x",
                "");

        Map<String, String> values = PropertiesParser.parse(content, "test.properties");

        assertThat(values).containsEntry("first", "one");
        assertThat(values).containsEntry("second", "two");
        assertThat(values).containsEntry("third", "three");
        assertThat(values).containsEntry("continued", "abc");
        assertThat(values).containsEntry("escaped", "hello\nworld=x");
    }

    @Test
    void missingKeyReportsFileLineAndReason(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("broken.properties");
        Files.writeString(file, "a = 1\n= orphan\n");

        assertThatThrownBy(() -> ConfigLoader.builder().configFile(file).build().load(Object.class))
                .isInstanceOf(ConfigParseException.class)
                .hasMessageContaining("文件=" + file)
                .hasMessageContaining("行号=2")
                .hasMessageContaining("缺少配置键");
    }

    @Test
    void invalidUnicodeEscapeReportsExactLine(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("broken.properties");
        Files.writeString(file, "good=1\nbad=\\uZZ99\n");

        assertThatThrownBy(() -> ConfigLoader.builder().configFile(file).build().load(Object.class))
                .isInstanceOf(ConfigParseException.class)
                .hasMessageContaining("文件=" + file)
                .hasMessageContaining("行号=2")
                .hasMessageContaining("Unicode");
    }

    @Test
    void invalidEscapeSequenceReportsExactLine(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("broken.properties");
        Files.writeString(file, "key=val\\q\n");

        assertThatThrownBy(() -> ConfigLoader.builder().configFile(file).build().load(Object.class))
                .isInstanceOf(ConfigParseException.class)
                .hasMessageContaining("行号=1")
                .hasMessageContaining("非法的转义序列");
    }

    @Test
    void errorInsideContinuedEntryReportsThePhysicalLine(@TempDir Path dir) throws Exception {
        // 第 2 行以单反斜杠续行，第 3 行携带非法转义 \q，行号定位到第 3 行
        Path file = dir.resolve("broken.properties");
        Files.writeString(file, "ok=1\nmulti=line1\\\nline2\\q\n");

        assertThatThrownBy(() -> ConfigLoader.builder().configFile(file).build().load(Object.class))
                .isInstanceOf(ConfigParseException.class)
                .hasMessageContaining("文件=" + file)
                .hasMessageContaining("行号=3")
                .hasMessageContaining("非法的转义序列");
    }

    @Test
    void missingFileIsTreatedAsEmpty(@TempDir Path dir) {
        Path file = dir.resolve("absent.properties");
        Map<String, String> values = PropertiesParser.parse(file,
                java.nio.charset.StandardCharsets.UTF_8);
        assertThat(values).isEmpty();
    }

    @Test
    void duplicateKeysLastOneWins(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("dup.properties");
        Files.writeString(file, "key=first\nkey=second\n");

        Map<String, String> values = PropertiesParser.parse(file, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(values).containsEntry("key", "second");
    }
}
