package com.odin2.odinsettings.tests;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

final class ResourceContractTest {
    static void verify() {
        Path repo = Path.of(requiredProperty("odin.repo_dir"));
        assertSettingsTheme(repo.resolve("res/values/styles.xml"));
        assertLocaleMatches(repo, "values-zh-rTW");
        assertLocaleMatches(repo, "values-zh-rCN");
        assertControllerLabelsAreLocalized(repo.resolve(
                "src/com/odin2/odinsettings/ControllerTestActivity.java"));
        assertHandheldActivityLayout(repo);
    }

    private static void assertSettingsTheme(Path stylesPath) {
        Document document = parse(stylesPath);
        NodeList styles = document.getElementsByTagName("style");
        for (int i = 0; i < styles.getLength(); i++) {
            Element style = (Element) styles.item(i);
            if ("OdinSettingsTheme".equals(style.getAttribute("name"))) {
                assertEquals("@android:style/Theme.DeviceDefault.Settings",
                        style.getAttribute("parent"), "OdinSettingsTheme parent");
                String xml = read(stylesPath);
                assertFalse(xml.contains("windowLightStatusBar"),
                        "theme must not force light status bar icons");
                assertFalse(xml.contains("colorAccent"),
                        "theme must inherit the system accent color");
                return;
            }
        }
        throw new AssertionError("Missing OdinSettingsTheme");
    }

    private static void assertLocaleMatches(Path repo, String locale) {
        assertEquals(resourceNames(repo.resolve("res/values/strings.xml"), "string"),
                resourceNames(repo.resolve("res/" + locale + "/strings.xml"), "string"),
                locale + " string resources");
        assertEquals(resourceNames(repo.resolve("res/values/arrays.xml"), "string-array"),
                resourceNames(repo.resolve("res/" + locale + "/arrays.xml"), "string-array"),
                locale + " array resources");
    }

    private static Set<String> resourceNames(Path path, String tagName) {
        Document document = parse(path);
        NodeList resources = document.getElementsByTagName(tagName);
        Set<String> names = new HashSet<>();
        for (int i = 0; i < resources.getLength(); i++) {
            names.add(((Element) resources.item(i)).getAttribute("name"));
        }
        return names;
    }

    private static void assertControllerLabelsAreLocalized(Path activityPath) {
        String source = read(activityPath);
        assertFalse(source.contains(".displayName"),
                "controller UI must use localized resources, not domain displayName fields");
        assertTrue(source.contains("ControllerDisplayNames"),
                "controller UI must use the Android resource mapper");
    }

    private static void assertHandheldActivityLayout(Path repo) {
        String mainActivity = read(repo.resolve(
                "src/com/odin2/odinsettings/MainSettingsActivity.java"));
        assertTrue(mainActivity.contains("boolean onIsMultiPane()"),
                "main settings must define its handheld pane policy");
        assertTrue(mainActivity.contains("return false;"),
                "main settings must not leave half the landscape display empty");
        assertTrue(mainActivity.contains("ControllerNavigation.translateConfirm"),
                "main settings must translate gamepad A into a UI confirm action");
        assertTrue(mainActivity.contains("ControllerNavigation.isBack"),
                "main settings must handle gamepad B as back");
        assertTrue(mainActivity.contains("focusFirstEnabledPreference"),
                "main settings must expose an initial D-pad focus target");

        String controllerTest = read(repo.resolve(
                "src/com/odin2/odinsettings/ControllerTestActivity.java"));
        assertTrue(controllerTest.contains("setDisplayHomeAsUpEnabled(true)"),
                "controller test must provide visible up navigation");
        assertTrue(controllerTest.contains("android.R.id.home"),
                "controller test must handle the up affordance");
        assertTrue(controllerTest.contains("ControllerNavigation.isBack"),
                "controller test must remain escapable with gamepad B");

        String preferences = read(repo.resolve("res/xml/main_preferences.xml"));
        assertTrue(preferences.contains("ControllerListPreference"),
                "profile chooser must use the controller-aware dialog preference");
        String listPreference = read(repo.resolve(
                "src/com/odin2/odinsettings/widget/ControllerListPreference.java"));
        assertTrue(listPreference.contains("setOnKeyListener"),
                "profile dialog must receive controller navigation events");
        assertTrue(listPreference.contains("performItemClick"),
                "profile dialog must activate its focused row with gamepad A");
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

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isEmpty()) {
            throw new AssertionError("Missing system property " + name);
        }
        return value;
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }

    private static void assertFalse(boolean value, String message) {
        if (value) {
            throw new AssertionError(message);
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private ResourceContractTest() {}
}
