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
        UiResourceContractTest.verify(repo);
        assertSettingsTheme(repo.resolve("res/values/styles.xml"));
        assertLocaleConfig(repo);
        assertLocaleMatches(repo, "values-zh-rTW");
        assertLocaleMatches(repo, "values-zh-rCN");
        assertStableControllerProfileValues(repo.resolve("res/values/arrays.xml"));
        assertControllerLabelsAreLocalized(repo.resolve(
                "src/com/odin2/odinsettings/ControllerTestActivity.java"));
        assertHandheldActivityLayout(repo);
    }

    private static void assertLocaleConfig(Path repo) {
        String manifest = read(repo.resolve("AndroidManifest.xml"));
        assertTrue(manifest.contains("android:localeConfig=\"@xml/locales_config\""),
                "application must declare its platform locale config");

        Document document = parse(repo.resolve("res/xml/locales_config.xml"));
        NodeList locales = document.getElementsByTagName("locale");
        Set<String> names = new HashSet<>();
        for (int i = 0; i < locales.getLength(); i++) {
            names.add(((Element) locales.item(i)).getAttribute("android:name"));
        }
        Set<String> expected = new HashSet<>();
        expected.add("en");
        expected.add("zh-CN");
        expected.add("zh-TW");
        assertEquals(expected, names, "platform locale config");
    }

    private static void assertSettingsTheme(Path stylesPath) {
        Document document = parse(stylesPath);
        NodeList styles = document.getElementsByTagName("style");
        assertEquals(1, styles.getLength(),
                "app resources must define exactly one theme style");
        for (int i = 0; i < styles.getLength(); i++) {
            Element style = (Element) styles.item(i);
            if ("OdinSettingsTheme".equals(style.getAttribute("name"))) {
                assertEquals("@android:style/Theme.DeviceDefault.Settings",
                        style.getAttribute("parent"), "OdinSettingsTheme parent");
                assertEquals(0, style.getElementsByTagName("item").getLength(),
                        "OdinSettingsTheme must inherit all system colors and appearance");
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
        Set<String> baseArrays = resourceNames(
                repo.resolve("res/values/arrays.xml"), "string-array");
        baseArrays.remove("controller_profile_values");
        assertEquals(baseArrays,
                resourceNames(repo.resolve("res/" + locale + "/arrays.xml"), "string-array"),
                locale + " array resources");
    }

    private static void assertStableControllerProfileValues(Path arraysPath) {
        Document document = parse(arraysPath);
        NodeList arrays = document.getElementsByTagName("string-array");
        for (int i = 0; i < arrays.getLength(); i++) {
            Element array = (Element) arrays.item(i);
            if ("controller_profile_values".equals(array.getAttribute("name"))) {
                assertEquals("false", array.getAttribute("translatable"),
                        "controller profile values must not be translated");
                return;
            }
        }
        throw new AssertionError("Missing controller_profile_values");
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
        assertTrue(mainActivity.contains("onWindowFocusChanged"),
                "main settings must restore controller focus after dialogs close");
        assertTrue(mainActivity.contains("activateControllerFocus"),
                "main settings must restore selection when controller input follows touch");
        assertTrue(mainActivity.contains("MotionEvent.ACTION_DOWN"),
                "main settings must remember the row last chosen by touch");

        String controllerTest = read(repo.resolve(
                "src/com/odin2/odinsettings/ControllerTestActivity.java"));
        assertTrue(controllerTest.contains("setDisplayHomeAsUpEnabled(true)"),
                "controller test must provide visible up navigation");
        assertTrue(controllerTest.contains("android.R.id.home"),
                "controller test must handle the up affordance");
        assertTrue(controllerTest.contains("ControllerNavigation.isBack"),
                "controller test must remain escapable with gamepad B");
        assertTrue(controllerTest.contains("ControllerNavigation.translateConfirm"),
                "controller test must use gamepad A as focused-control activation");
        assertTrue(controllerTest.contains("R.layout.controller_test_activity"),
                "controller test must inflate its resource-defined layout");
        assertFalse(controllerTest.contains("setTextSize("),
                "controller test must defer text sizing to the system theme");
        assertFalse(controllerTest.contains("dp("),
                "controller test must not calculate fixed pixel spacing");
        assertTrue(controllerTest.contains(
                        "getString(ControllerDisplayNames.buttonName(physical))"),
                "controller test must resolve button resource IDs through the activity locale");

        String controllerLayout = read(repo.resolve("res/layout/controller_test_activity.xml"));
        assertTrue(controllerLayout.contains("<ScrollView"),
                "controller test must scroll in landscape and at large font scales");
        assertTrue(controllerLayout.contains("android:fillViewport=\"true\""),
                "controller test scroll content must fill the viewport");
        assertTrue(controllerLayout.contains("@string/done"),
                "controller test must expose a visible focusable exit action");
        assertTrue(controllerLayout.contains("android:labelFor="),
                "controller test status labels must identify their dynamic values");
        assertTrue(controllerLayout.contains("android:accessibilityLiveRegion=\"polite\""),
                "controller test results must be exposed as accessibility live regions");
        assertTrue(controllerLayout.contains("android:layoutDirection=\"locale\""),
                "controller test layout must follow locale direction");
        assertTrue(controllerLayout.contains("android:paddingStart="),
                "controller test layout must use start padding");
        assertTrue(controllerLayout.contains("android:paddingEnd="),
                "controller test layout must use end padding");
        assertFalse(controllerLayout.contains("paddingLeft"),
                "controller test layout must not use physical left padding");
        assertFalse(controllerLayout.contains("paddingRight"),
                "controller test layout must not use physical right padding");
        assertTrue(controllerLayout.contains("?android:attr/textAppearance"),
                "controller test must use system text appearances that scale with font size");

        String preferences = read(repo.resolve("res/xml/main_preferences.xml"));
        assertTrue(preferences.contains("ControllerListPreference"),
                "profile chooser must use the controller-aware dialog preference");
        String listPreference = read(repo.resolve(
                "src/com/odin2/odinsettings/widget/ControllerListPreference.java"));
        assertTrue(listPreference.contains("setOnKeyListener"),
                "profile dialog must receive controller navigation events");
        assertTrue(listPreference.contains("performItemClick"),
                "profile dialog must activate its focused row with gamepad A");
        assertTrue(listPreference.contains("showDialog(Bundle state)"),
                "profile dialog must initialize an explicit controller selection");
        assertTrue(listPreference.contains("requestFocus"),
                "profile dialog list must receive visible controller focus");
        assertTrue(listPreference.contains("setSelection"),
                "profile dialog must visibly select the checked row");
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
