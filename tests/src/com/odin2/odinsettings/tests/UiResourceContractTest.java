package com.odin2.odinsettings.tests;

import java.nio.file.Path;

final class UiResourceContractTest {
    static void verify(Path repo) {
        UiResourceContract.verify(repo);
        UiResourceContract.verifyFixture(repo.resolve("tests/fixtures/ui-contract/invalid"));
    }

    private UiResourceContractTest() {}
}
