package com.odin2.odinsettings.tests;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

final class UiResourceContract {
    private static final String SETTINGS_THEME =
            "@android:style/Theme.DeviceDefault.Settings";
    private static final Set<String> VISIBLE_TEXT_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "contentDescription", "dialogTitle", "hint", "label", "negativeButtonText",
            "neutralButtonText", "positiveButtonText", "summary", "text", "title"));
    private static final Set<String> COLOR_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "background", "colorAccent", "fillColor", "foreground", "navigationBarColor",
            "statusBarColor", "textColor", "textColorHint", "textColorLink",
            "windowBackground"));
    private static final Set<String> PHYSICAL_DIRECTION_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "layout_alignLeft", "layout_alignRight", "layout_marginLeft", "layout_marginRight",
            "paddingLeft", "paddingRight"));
    private static final Pattern STRING_RESOURCE =
            Pattern.compile("@(?:android:)?string/[A-Za-z0-9_]+");
    private static final Pattern HEX_COLOR =
            Pattern.compile("#[0-9a-fA-F]{3,8}");
    private static final Pattern JAVA_HARDCODED_TEXT = Pattern.compile(
            "\\b(?:setText|setTitle|setSummary|setMessage|setContentDescription|"
                    + "setPositiveButton|setNegativeButton|setNeutralButton)\\s*\\(\\s*\"");
    private static final Pattern JAVA_HARDCODED_TOAST = Pattern.compile(
            "Toast\\.makeText\\s*\\([^,]+,\\s*\"");
    private static final Pattern JAVA_FIXED_COLOR = Pattern.compile(
            "\\b(?:setTextColor|setBackgroundColor)\\s*\\(|\\bColor\\.[A-Z_]+\\b");
    private static final Pattern XML_FIXED_DIMENSION = Pattern.compile(
            "[0-9]+(?:\\.[0-9]+)?(?:dp|sp|px|pt|in|mm)");
    private static final Pattern JAVA_FIXED_DIMENSION = Pattern.compile(
            "\\bsetTextSize\\s*\\(|\\b(?:dp|sp)\\s*\\(\\s*[0-9]+");
    private static final Pattern FORCED_THEME = Pattern.compile(
            "forceDark|nightMode|setNightMode|setDefaultNightMode|setLocalNightMode|MODE_NIGHT|"
                    + "windowLightStatusBar|windowLightNavigationBar|\\bsetTheme\\s*\\(|"
                    + "Theme(?:\\.[A-Za-z0-9_]+)*\\.Light\\b");

    static void verify(Path repo) {
        List<String> violations = inspect(repo);
        if (!violations.isEmpty()) {
            throw new AssertionError("UI resource contract violations:\n  - "
                    + String.join("\n  - ", violations));
        }
    }

    static void verifyFixture(Path fixture) {
        List<String> violations = inspect(fixture);
        assertCategory(violations, "hardcoded-text:");
        assertCategory(violations, "fixed-color:");
        assertCategory(violations, "fixed-dimension:");
        assertCategory(violations, "forced-theme:");
        assertCategory(violations, "locale-drift:");
        assertCategory(violations, "rtl-layout:");
    }

    private static List<String> inspect(Path repo) {
        List<String> violations = new ArrayList<>();
        inspectSettingsTheme(repo.resolve("res/values/styles.xml"), violations);
        inspectLocaleParity(repo.resolve("res"), violations);
        Path manifest = repo.resolve("AndroidManifest.xml");
        inspectApplicationTheme(manifest, violations);
        inspectXmlTree(repo, manifest, violations);
        inspectXmlDirectory(repo, repo.resolve("res"), violations);
        inspectJavaDirectory(repo, repo.resolve("src"), violations);
        inspectControllerDisplayMetadata(repo, violations);
        inspectControllerTestLayout(repo, violations);
        return violations;
    }

    private static void inspectSettingsTheme(Path stylesPath, List<String> violations) {
        if (!Files.isRegularFile(stylesPath)) {
            violations.add("forced-theme: missing " + stylesPath);
            return;
        }
        Document document = parse(stylesPath);
        NodeList styles = document.getElementsByTagName("style");
        for (int index = 0; index < styles.getLength(); index++) {
            Element style = (Element) styles.item(index);
            if (!"OdinSettingsTheme".equals(style.getAttribute("name"))) {
                continue;
            }
            if (!SETTINGS_THEME.equals(style.getAttribute("parent"))) {
                violations.add("forced-theme: OdinSettingsTheme must inherit " + SETTINGS_THEME);
            }
            return;
        }
        violations.add("forced-theme: missing OdinSettingsTheme");
    }

    private static void inspectApplicationTheme(Path manifestPath, List<String> violations) {
        if (!Files.isRegularFile(manifestPath)) {
            violations.add("forced-theme: missing " + manifestPath);
            return;
        }
        Document document = parse(manifestPath);
        NodeList applications = document.getElementsByTagName("application");
        if (applications.getLength() != 1) {
            violations.add("forced-theme: manifest must contain one application");
            return;
        }
        Element application = (Element) applications.item(0);
        if (!"@style/OdinSettingsTheme".equals(application.getAttribute("android:theme"))) {
            violations.add("forced-theme: application must use @style/OdinSettingsTheme");
        }
        NodeList activities = application.getElementsByTagName("activity");
        for (int index = 0; index < activities.getLength(); index++) {
            Element activity = (Element) activities.item(index);
            String theme = activity.getAttribute("android:theme");
            if (!theme.isEmpty() && !"@style/OdinSettingsTheme".equals(theme)) {
                violations.add("forced-theme: activity overrides the application theme: "
                        + activity.getAttribute("android:name"));
            }
        }
    }

    private static void inspectLocaleParity(Path resDir, List<String> violations) {
        Map<String, String> english = resourceValues(resDir.resolve("values"));
        for (String locale : Arrays.asList("values-zh-rCN", "values-zh-rTW")) {
            Path localeDir = resDir.resolve(locale);
            Map<String, String> localized = resourceValues(localeDir);
            if (!english.keySet().equals(localized.keySet())) {
                Set<String> missing = new HashSet<>(english.keySet());
                missing.removeAll(localized.keySet());
                Set<String> extra = new HashSet<>(localized.keySet());
                extra.removeAll(english.keySet());
                violations.add("locale-drift: " + locale + " missing=" + missing + " extra=" + extra);
            }
            for (Map.Entry<String, String> entry : localized.entrySet()) {
                if (entry.getValue().trim().isEmpty()) {
                    violations.add("locale-drift: " + locale + " has blank " + entry.getKey());
                }
            }
        }
    }

    private static Map<String, String> resourceValues(Path valuesDir) {
        Map<String, String> resources = new HashMap<>();
        if (!Files.isDirectory(valuesDir)) {
            return resources;
        }
        for (Path path : xmlFiles(valuesDir)) {
            Document document = parse(path);
            NodeList children = document.getDocumentElement().getChildNodes();
            for (int index = 0; index < children.getLength(); index++) {
                Node node = children.item(index);
                if (node.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element element = (Element) node;
                String tag = element.getTagName();
                if (!"string".equals(tag) && !"string-array".equals(tag)
                        && !"plurals".equals(tag)) {
                    continue;
                }
                String key = tag + "/" + element.getAttribute("name");
                String previous = resources.put(key, element.getTextContent());
                if (previous != null) {
                    throw new AssertionError("Duplicate resource " + key + " in " + valuesDir);
                }
                if ("string-array".equals(tag)
                        && !element.getAttribute("name").endsWith("_values")) {
                    inspectDisplayArray(path, element);
                }
            }
        }
        return resources;
    }

    private static void inspectDisplayArray(Path path, Element array) {
        NodeList items = array.getElementsByTagName("item");
        for (int index = 0; index < items.getLength(); index++) {
            String value = items.item(index).getTextContent().trim();
            if (!STRING_RESOURCE.matcher(value).matches()) {
                throw new AssertionError("Visible array items must reference @string resources: "
                        + path + " " + array.getAttribute("name"));
            }
        }
    }

    private static void inspectXmlDirectory(Path repo, Path directory,
            List<String> violations) {
        if (!Files.isDirectory(directory)) {
            return;
        }
        for (Path path : xmlFiles(directory)) {
            inspectXmlTree(repo, path, violations);
        }
    }

    private static void inspectXmlTree(Path repo, Path path, List<String> violations) {
        if (!Files.isRegularFile(path)) {
            return;
        }
        String source = read(path);
        if (FORCED_THEME.matcher(source).find()) {
            violations.add("forced-theme: " + relative(repo, path));
        }
        if (HEX_COLOR.matcher(source).find()) {
            violations.add("fixed-color: " + relative(repo, path) + " contains a hex color");
        }
        Document document = parse(path);
        NodeList elements = document.getElementsByTagName("*");
        for (int elementIndex = 0; elementIndex < elements.getLength(); elementIndex++) {
            NamedNodeMap attributes = elements.item(elementIndex).getAttributes();
            for (int attributeIndex = 0; attributeIndex < attributes.getLength(); attributeIndex++) {
                Node attribute = attributes.item(attributeIndex);
                String name = localName(attribute.getNodeName());
                String value = attribute.getNodeValue().trim();
                if (VISIBLE_TEXT_ATTRIBUTES.contains(name)
                        && !STRING_RESOURCE.matcher(value).matches()) {
                    violations.add("hardcoded-text: " + relative(repo, path) + " "
                            + attribute.getNodeName() + "=" + value);
                }
                if (COLOR_ATTRIBUTES.contains(name)
                        && (value.startsWith("#") || value.startsWith("@color/")
                                || value.startsWith("@android:color/"))) {
                    violations.add("fixed-color: " + relative(repo, path) + " "
                            + attribute.getNodeName() + "=" + value);
                }
                if (PHYSICAL_DIRECTION_ATTRIBUTES.contains(name)) {
                    violations.add("rtl-layout: " + relative(repo, path) + " "
                            + attribute.getNodeName() + "=" + value);
                }
                if (relative(repo, path).startsWith("res/layout/")
                        && XML_FIXED_DIMENSION.matcher(value).matches()) {
                    violations.add("fixed-dimension: " + relative(repo, path) + " "
                            + attribute.getNodeName() + "=" + value);
                }
            }
        }
    }

    private static void inspectJavaDirectory(Path repo, Path directory,
            List<String> violations) {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(path -> path.toString().endsWith(".java")).sorted().forEach(path -> {
                String source = read(path);
                String relative = relative(repo, path);
                if (JAVA_HARDCODED_TEXT.matcher(source).find()
                        || JAVA_HARDCODED_TOAST.matcher(source).find()) {
                    violations.add("hardcoded-text: " + relative);
                }
                if (JAVA_FIXED_COLOR.matcher(source).find()
                        || HEX_COLOR.matcher(source).find()) {
                    violations.add("fixed-color: " + relative);
                }
                if (JAVA_FIXED_DIMENSION.matcher(source).find()) {
                    violations.add("fixed-dimension: " + relative);
                }
                if (FORCED_THEME.matcher(source).find()) {
                    violations.add("forced-theme: " + relative);
                }
            });
        } catch (IOException exception) {
            throw new AssertionError("Cannot scan " + directory, exception);
        }
    }

    private static void inspectControllerDisplayMetadata(Path repo, List<String> violations) {
        for (String sourceFile : Arrays.asList(
                "src/com/odin2/odinsettings/domain/ControllerButton.java",
                "src/com/odin2/odinsettings/domain/ControllerProfile.java",
                "src/com/odin2/odinsettings/domain/ControllerProfiles.java")) {
            Path path = repo.resolve(sourceFile);
            if (Files.isRegularFile(path) && (read(path).contains("displayName")
                    || read(path).contains("description"))) {
                violations.add("localized-controller-display-metadata: " + sourceFile);
            }
        }
    }

    private static void inspectControllerTestLayout(Path repo, List<String> violations) {
        Path activity = repo.resolve("src/com/odin2/odinsettings/ControllerTestActivity.java");
        if (!Files.isRegularFile(activity)) {
            return;
        }
        Path layout = repo.resolve("res/layout/controller_test_activity.xml");
        if (!Files.isRegularFile(layout)) {
            violations.add("large-font: missing " + layout);
            return;
        }
        String source = read(layout);
        if (!source.contains("<ScrollView") || !source.contains("android:fillViewport=\"true\"")) {
            violations.add("large-font: controller test must use a viewport-filling ScrollView");
        }
        if (!source.contains("android:layoutDirection=\"locale\"")) {
            violations.add("rtl-layout: controller test must inherit locale layout direction");
        }
        if (!source.contains("android:paddingStart=") || !source.contains("android:paddingEnd=")) {
            violations.add("rtl-layout: controller test must use start/end content padding");
        }
        if (!source.contains("?android:attr/textAppearance")) {
            violations.add("large-font: controller test must use theme text appearances");
        }
    }

    private static List<Path> xmlFiles(Path directory) {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(path -> path.toString().endsWith(".xml")).sorted().forEach(files::add);
        } catch (IOException exception) {
            throw new AssertionError("Cannot scan " + directory, exception);
        }
        return files;
    }

    private static Document parse(Path path) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(path.toFile());
        } catch (IOException | ParserConfigurationException | SAXException exception) {
            throw new AssertionError("Cannot parse " + path, exception);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AssertionError("Cannot read " + path, exception);
        }
    }

    private static String localName(String qualifiedName) {
        int separator = qualifiedName.indexOf(':');
        return separator >= 0 ? qualifiedName.substring(separator + 1) : qualifiedName;
    }

    private static String relative(Path repo, Path path) {
        return repo.relativize(path).toString();
    }

    private static void assertCategory(List<String> violations, String prefix) {
        for (String violation : violations) {
            if (violation.startsWith(prefix)) {
                return;
            }
        }
        throw new AssertionError("Invalid UI fixture did not trigger " + prefix
                + " violations: " + violations);
    }

    private UiResourceContract() {}
}
