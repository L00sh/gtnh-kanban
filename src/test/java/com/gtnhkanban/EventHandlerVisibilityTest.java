package com.gtnhkanban;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.Test;

/**
 * Forge calls event handlers through classes it generates in its own class loader. Those can only reach a handler
 * whose class and method are public; otherwise the first event crashes the game with IllegalAccessError. This checks
 * every handler in the source, so a new one cannot ship package-private.
 */
public class EventHandlerVisibilityTest {

    private static final Pattern CLASS = Pattern
        .compile("^((?:public |protected |private |static |final |abstract )*)class (\\w+)", Pattern.MULTILINE);
    private static final Pattern HANDLER = Pattern.compile("@SubscribeEvent\\s+((?:\\w+ )*)void (\\w+)");

    @Test
    public void everyEventHandlerClassAndMethodIsPublic() throws IOException {
        List<String> problems = new ArrayList<String>();
        try (Stream<Path> files = Files.walk(Paths.get("src/main/java"))) {
            for (Path file : (Iterable<Path>) files.filter(
                path -> path.toString()
                    .endsWith(".java"))::iterator) {
                String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                if (!source.contains("@SubscribeEvent")) continue;
                Matcher declaration = CLASS.matcher(source);
                if (declaration.find() && !declaration.group(1)
                    .contains("public"))
                    problems.add(file.getFileName() + ": class " + declaration.group(2) + " is not public");
                Matcher handler = HANDLER.matcher(source);
                while (handler.find()) {
                    if (!handler.group(1)
                        .contains("public"))
                        problems.add(file.getFileName() + ": handler " + handler.group(2) + " is not public");
                }
            }
        }
        assertTrue(problems.toString(), problems.isEmpty());
    }
}
