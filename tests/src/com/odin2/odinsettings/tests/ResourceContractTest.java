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
        assertDiscoverableEntrypoints(repo);
        assertLocaleMatches(repo, "values-zh-rTW");
        assertLocaleMatches(repo, "values-zh-rCN");
        assertStableControllerProfileValues(repo.resolve("res/values/arrays.xml"));
        assertSystemControllerProfileClient(repo);
        assertControllerLabelsAreLocalized(repo.resolve(
                "src/com/odin2/odinsettings/ControllerTestActivity.java"));
        assertHandheldActivityLayout(repo);
        assertFanControlIsAllowlisted(repo);
        assertAppDoesNotOwnFanSysfs(repo);
        assertControllerColdBootPrimeIsBounded(repo);
    }

    private static void assertSystemControllerProfileClient(Path repo) {
        String build = read(repo.resolve("Android.bp"));
        assertTrue(build.contains("\"com.ayn.controller-java\""),
                "Odin Settings must link the controller Java static library");

        String preferences = read(repo.resolve("res/xml/main_preferences.xml"));
        assertTrue(preferences.contains("android:key=\"system_controller_profile\""),
                "the profile list must own the system profile key");
        assertFalse(preferences.contains("preview_controller_profile")
                        || preferences.contains("system_controller_mapping"),
                "preview and disabled system mapping rows must be converged");

        String client = read(repo.resolve(
                "src/com/odin2/odinsettings/platform/AidlControllerHardwareAdapter.java"));
        assertTrue(client.contains("com.ayn.controller.IOdinController/default"),
                "controller client must use the fixed service instance");
        assertTrue(client.contains("import com.ayn.controller.ControllerProfileResponse;"),
                "controller client must use the generated response parcelable");
        assertTrue(client.contains(
                        "ControllerProfileResponse setResponse = service.setProfile(serviceProfile);")
                        && client.contains(
                                "ControllerProfileResponse getResponse = service.getProfile();"),
                "set and get calls must retain their typed parcelable responses");
        for (String field : new String[] {"result", "requestedProfile", "activeProfile"}) {
            assertTrue(client.contains("setResponse." + field)
                            && client.contains("getResponse." + field),
                    "controller client must map parcelable field " + field);
        }
        assertFalse(client.contains("mapReadResult(service.getProfile())")
                        || client.contains("mapSetResult(service.setProfile"),
                "controller parcelables must never be passed to integer mappers");
        assertFalse(client.contains("SystemProperties") || client.contains("/sys/")
                        || client.contains("/proc/"),
                "controller client must not bypass Binder with direct writes");
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

    private static void assertDiscoverableEntrypoints(Path repo) {
        Document manifest = parse(repo.resolve("AndroidManifest.xml"));
        Element mainActivity = findComponent(
                manifest, "activity", ".MainSettingsActivity");
        assertEquals("true", mainActivity.getAttribute("android:exported"),
                "main settings exported state");
        assertTrue(hasIntentFilter(mainActivity,
                        "com.android.settings.action.EXTRA_SETTINGS",
                        "android.intent.category.DEFAULT"),
                "main settings must expose a default Settings tile intent");
        assertEquals("com.android.settings.category.ia.system",
                metadataValue(mainActivity, "com.android.settings.category"),
                "Settings tile category");
        assertEquals("@string/app_name",
                metadataResource(mainActivity, "com.android.settings.title"),
                "Settings tile title");
        assertEquals("@string/settings_entry_summary",
                metadataResource(mainActivity, "com.android.settings.summary"),
                "Settings tile summary");
        assertEquals("@drawable/ic_settings_odin",
                metadataResource(mainActivity, "com.android.settings.icon"),
                "Settings tile icon");
        assertEquals("primary_profile_only",
                metadataValue(mainActivity, "com.android.settings.profile"),
                "Settings tile profile");
        assertFalse(hasIntentCategory(mainActivity, "android.intent.category.LAUNCHER"),
                "launcher discovery must be owned by a dedicated alias");

        Element launcherAlias = findComponent(manifest, "activity-alias", ".LauncherActivity");
        assertEquals(".MainSettingsActivity",
                launcherAlias.getAttribute("android:targetActivity"),
                "launcher alias target");
        assertEquals("true", launcherAlias.getAttribute("android:exported"),
                "launcher alias exported state");
        assertEquals("@string/app_name", launcherAlias.getAttribute("android:label"),
                "launcher alias label");
        assertEquals("@drawable/ic_settings_odin", launcherAlias.getAttribute("android:icon"),
                "launcher alias icon");
        assertTrue(hasIntentFilter(launcherAlias,
                        "android.intent.action.MAIN",
                        "android.intent.category.LAUNCHER"),
                "launcher alias must be discoverable from the app launcher");

        assertTrue(Files.isRegularFile(repo.resolve("res/drawable/ic_settings_odin.xml")),
                "discoverable entries must provide a dedicated icon");
        assertTrue(resourceNames(repo.resolve("res/values/strings.xml"), "string")
                        .contains("settings_entry_summary"),
                "Settings tile must provide a localized summary resource");
    }

    private static Element findComponent(Document document, String tagName, String name) {
        NodeList components = document.getElementsByTagName(tagName);
        for (int i = 0; i < components.getLength(); i++) {
            Element component = (Element) components.item(i);
            if (name.equals(component.getAttribute("android:name"))) {
                return component;
            }
        }
        throw new AssertionError("Missing " + tagName + " " + name);
    }

    private static boolean hasIntentFilter(Element component, String action, String category) {
        NodeList filters = component.getElementsByTagName("intent-filter");
        for (int i = 0; i < filters.getLength(); i++) {
            Element filter = (Element) filters.item(i);
            if (hasNamedElement(filter, "action", action)
                    && hasNamedElement(filter, "category", category)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasIntentCategory(Element component, String category) {
        return hasNamedElement(component, "category", category);
    }

    private static boolean hasNamedElement(Element parent, String tagName, String name) {
        NodeList elements = parent.getElementsByTagName(tagName);
        for (int i = 0; i < elements.getLength(); i++) {
            if (name.equals(((Element) elements.item(i)).getAttribute("android:name"))) {
                return true;
            }
        }
        return false;
    }

    private static String metadataResource(Element component, String name) {
        return metadataAttribute(component, name, "android:resource");
    }

    private static String metadataValue(Element component, String name) {
        return metadataAttribute(component, name, "android:value");
    }

    private static String metadataAttribute(Element component, String name, String attribute) {
        NodeList metadata = component.getElementsByTagName("meta-data");
        for (int i = 0; i < metadata.getLength(); i++) {
            Element item = (Element) metadata.item(i);
            if (name.equals(item.getAttribute("android:name"))) {
                return item.getAttribute(attribute);
            }
        }
        throw new AssertionError("Missing metadata " + name);
    }

    private static void assertControllerColdBootPrimeIsBounded(Path repo) {
        String manifest = read(repo.resolve("AndroidManifest.xml"));
        assertTrue(manifest.contains("android.permission.DEVICE_POWER"),
                "controller prime must declare the platform display-power permission");
        assertTrue(manifest.contains("android.intent.action.LOCKED_BOOT_COMPLETED"),
                "controller prime must run once from the direct-boot broadcast");
        assertTrue(manifest.contains("android:exported=\"false\""),
                "controller prime receiver must not be externally callable");

        String receiver = read(repo.resolve(
                "src/com/odin2/odinsettings/platform/ControllerColdBootReceiver.java"));
        assertTrue(receiver.contains("hasOdinGamepad(inputManager)"),
                "controller prime must skip the display cycle when gamepad is present");
        assertTrue(receiver.contains("public ControllerColdBootReceiver()"),
                "manifest receiver must expose a public no-argument constructor");
        assertTrue(receiver.contains("attemptConsumed = true"),
                "controller prime must consume its only attempt before the display write");
        assertTrue(receiver.contains("GO_TO_SLEEP_FLAG_NO_DOZE")
                        && receiver.contains("WAKE_REASON_APPLICATION"),
                "controller prime must use one bounded framework display cycle");
        assertFalse(receiver.contains("driver_ctl") || receiver.contains("mcupower")
                        || receiver.contains("/sys/") || receiver.contains("gpio"),
                "controller prime must never own kernel or MCU control paths");
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
        // Driven by the translatable attribute rather than a list of names, so
        // an array added later is covered without editing this test.
        Set<String> baseArrays = resourceNames(
                repo.resolve("res/values/arrays.xml"), "string-array");
        baseArrays.removeAll(untranslatableResourceNames(
                repo.resolve("res/values/arrays.xml"), "string-array"));
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

    private static Set<String> untranslatableResourceNames(Path path, String tagName) {
        Document document = parse(path);
        NodeList resources = document.getElementsByTagName(tagName);
        Set<String> names = new HashSet<>();
        for (int i = 0; i < resources.getLength(); i++) {
            Element resource = (Element) resources.item(i);
            if ("false".equals(resource.getAttribute("translatable"))) {
                names.add(resource.getAttribute("name"));
            }
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
        Document manifest = parse(repo.resolve("AndroidManifest.xml"));
        NodeList activities = manifest.getElementsByTagName("activity");
        boolean controllerTestDisablesPredictiveBack = false;
        for (int i = 0; i < activities.getLength(); i++) {
            Element activity = (Element) activities.item(i);
            String name = activity.getAttribute("android:name");
            String backCallback = activity.getAttribute(
                    "android:enableOnBackInvokedCallback");
            if (".ControllerTestActivity".equals(name)) {
                controllerTestDisablesPredictiveBack = "false".equals(backCallback);
            } else {
                assertFalse(activity.hasAttribute("android:enableOnBackInvokedCallback"),
                        "predictive back opt-out must be limited to controller test");
            }
        }
        assertTrue(controllerTestDisablesPredictiveBack,
                "controller test must receive legacy back key events");

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
        assertTrue(controllerTest.contains("InputManager.InputDeviceListener"),
                "controller test must track an already-published Android input device");
        assertTrue(controllerTest.contains("0x2020") && controllerTest.contains("0x3001"),
                "controller test must identify the published Odin2 gamepad");
        assertTrue(controllerTest.contains("dispatchGenericMotionEvent"),
                "controller test must receive joystick and trigger motion events");
        assertTrue(controllerTest.contains(
                        "rightX = centeredAxis(event, MotionEvent.AXIS_Z)")
                        && controllerTest.contains(
                                "rightY = centeredAxis(event, MotionEvent.AXIS_RZ)")
                        && controllerTest.contains(
                                "leftTrigger = triggerAxis(event, MotionEvent.AXIS_LTRIGGER)")
                        && controllerTest.contains(
                                "rightTrigger = triggerAxis(event, MotionEvent.AXIS_RTRIGGER)"),
                "controller test must read the post-keylayout Odin2 stick and trigger axes");
        String inputMapper = read(repo.resolve(
                "src/com/odin2/odinsettings/platform/AndroidControllerInputMapper.java"));
        assertTrue(inputMapper.contains("KeyEvent.KEYCODE_F1")
                        && inputMapper.contains("KeyEvent.KEYCODE_BACK")
                        && inputMapper.contains("ControllerButton.BACK"),
                "controller test must capture both BTN_BACK F1 and framework back keys");
        assertTrue(controllerTest.contains("content.requestFocus()"),
                "controller test must start in capture focus instead of focusing Done");
        assertTrue(controllerTest.contains(
                        "physical == ControllerButton.A && done.hasFocus()"),
                "gamepad A may activate Done only after explicit D-pad focus navigation");
        assertTrue(controllerTest.indexOf("showButton(physical)")
                        < controllerTest.indexOf("physical == ControllerButton.A && done.hasFocus()"),
                "controller test must record gamepad A before any focused Done activation");
        assertTrue(controllerTest.contains(
                        "ControllerScanCodeMapper.fromScanCode(event.getScanCode())"),
                "controller test must map the physical scan code");
        assertTrue(controllerTest.indexOf(
                        "ControllerScanCodeMapper.fromScanCode(event.getScanCode())")
                        < controllerTest.indexOf(
                                "AndroidControllerInputMapper.fromKeyCode(event.getKeyCode())"),
                "controller test must prefer scan code identity before keycode fallback");
        assertFalse(controllerTest.contains("ControllerNavigation.isBack"),
                "controller test must capture gamepad B instead of closing");
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

    private static void assertFanControlIsAllowlisted(Path repo) {
        Document preferences = parse(repo.resolve("res/xml/main_preferences.xml"));
        NodeList rows = preferences.getElementsByTagName(
                "com.odin2.odinsettings.widget.ControllerListPreference");
        Element fanMode = null;
        for (int i = 0; i < rows.getLength(); i++) {
            Element row = (Element) rows.item(i);
            if ("fan_mode".equals(row.getAttribute("android:key"))) {
                fanMode = row;
                break;
            }
        }
        assertTrue(fanMode != null, "fan mode choice must exist");
        assertEquals("@array/fan_mode_entries", fanMode.getAttribute("android:entries"),
                "fan mode entries");
        assertEquals("@array/fan_mode_values", fanMode.getAttribute("android:entryValues"),
                "fan mode values");
        assertEquals("false", fanMode.getAttribute("android:persistent"),
                "fan mode must not persist a stale hardware request");

        String arrays = read(repo.resolve("res/values/arrays.xml"));
        assertTrue(arrays.contains("name=\"fan_mode_values\" translatable=\"false\""),
                "fan mode values must be stable and non-translatable");
        assertTrue(arrays.contains("<item>off</item>"), "fan modes must include off");
        assertTrue(arrays.contains("<item>quiet</item>"), "fan modes must include quiet");
        assertTrue(arrays.contains("<item>sport</item>"), "fan modes must include sport");
        assertFalse(arrays.toLowerCase().contains("custom"),
                "fan UI must not expose a custom mode");
        assertFalse(read(repo.resolve("res/xml/main_preferences.xml")).contains("SeekBar"),
                "fan UI must not expose an arbitrary high-time slider");

        String activity = read(repo.resolve(
                "src/com/odin2/odinsettings/MainSettingsActivity.java"));
        assertTrue(activity.contains("protected void onResume()"),
                "fan status must refresh when the settings screen resumes");
        assertTrue(activity.contains("fanApplyDispatcher.submitRead("),
                "fan status reads must dispatch outside the UI thread");
        assertFalse(activity.contains("renderFanStatus(fanController.read())"),
                "fan status reads must not block the UI thread");
        assertTrue(activity.contains(
                        "private static final ExecutorService FAN_WORKER = "
                                + "Executors.newSingleThreadExecutor()"),
                "all activity generations must share one process fan worker");
        assertTrue(activity.contains("fanApplyDispatcher.submit("),
                "fan mode changes must dispatch outside the preference callback");
        assertFalse(activity.contains("renderFanStatus(fanController.apply("),
                "fan mode changes must not synchronously call Binder on the UI thread");
        assertTrue(activity.contains("fanModePreference.setEnabled(false)"),
                "fan preference must be disabled while a request is pending");
        assertTrue(activity.contains("isFinishing() || isDestroyed()"),
                "fan completion must reject stale activity UI updates");
        assertTrue(activity.contains("fanApplyDispatcher.close()"),
                "activity destruction must converge the fan worker");
        assertTrue(activity.contains("return false;"),
                "preference framework must wait for a confirmed fan response");
        assertTrue(activity.contains("setOnPreferenceChangeListener"),
                "fan mode must be applied only after a confirmed radio choice");
        assertTrue(activity.contains("protected void onSaveInstanceState(Bundle outState)"),
                "requested fan mode must survive activity recreation");
        assertTrue(activity.contains("STATE_REQUESTED_FAN_MODE"),
                "requested fan mode restoration must use saved instance state");

        String blueprint = read(repo.resolve("Android.bp"));
        assertTrue(blueprint.contains("\"com.ayn.fan-java\""),
                "Odin Settings must depend on the private fan AIDL Java library");

        String aidlController = read(repo.resolve(
                "src/com/odin2/odinsettings/platform/AidlFanController.java"));
        assertTrue(aidlController.contains("com.ayn.fan.IOdinFan/default"),
                "fan client must use the private default service instance");
        assertTrue(aidlController.contains("ServiceManager.checkService(SERVICE_NAME)"),
                "fan client must perform non-blocking service lookup on its worker");
        assertFalse(aidlController.contains("OWNER_TOKEN")
                        || aidlController.contains("new Binder()"),
                "fan client must not own the persistent daemon mode");
        assertTrue(aidlController.contains("private static final AidlFanController INSTANCE"),
                "fan client cache must live for the app process");
        assertTrue(aidlController.contains("IOdinFan.Stub.asInterface(binder)"),
                "fan client must bind through the generated AIDL interface");
        assertTrue(aidlController.contains("binder.linkToDeath("),
                "fan client must clear its cache through a death recipient");
        assertTrue(aidlController.contains("connection.read("),
                "getStatus must use the tested read-reacquire seam");
        assertTrue(aidlController.contains("connection.write("),
                "setMode must use the tested no-retry write seam");
        assertTrue(aidlController.contains("service.setMode(mode.serviceValue)"),
                "setMode must not send an app process owner token");

        for (String locale : new String[] {
                "values", "values-zh-rCN", "values-zh-rTW"}) {
            String strings = read(repo.resolve("res/" + locale + "/strings.xml"));
            assertFalse(strings.toLowerCase().contains("duty"),
                    locale + " fan status must not label high-time as duty");
            assertFalse(strings.contains("RPM"),
                    locale + " fan status must not label tach as RPM");
        }
    }

    private static void assertAppDoesNotOwnFanSysfs(Path repo) {
        StringBuilder production = new StringBuilder(read(repo.resolve("Android.bp")));
        try (java.util.stream.Stream<Path> paths = Files.walk(repo.resolve("src"))) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .forEach(path -> production.append('\n').append(read(path)));
        } catch (IOException exception) {
            throw new AssertionError("Cannot inspect production app source", exception);
        }

        String source = production.toString();
        assertFalse(source.contains("System.loadLibrary"),
                "production app source must not load JNI libraries");
        assertFalse(source.contains("jni_libs"),
                "production app blueprint must not package JNI libraries");
        assertFalse(source.contains("gpio5_pwm2"),
                "production app source must not contain direct fan sysfs paths");
        Path retiredJni = repo.resolve("jni");
        if (Files.exists(retiredJni)) {
            try (java.util.stream.Stream<Path> paths = Files.walk(retiredJni)) {
                assertFalse(paths.anyMatch(Files::isRegularFile),
                        "retired fan JNI production sources must be removed");
            } catch (IOException exception) {
                throw new AssertionError("Cannot inspect retired JNI directory", exception);
            }
        }
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
