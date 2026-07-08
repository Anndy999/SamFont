package com.samfont.core.shizuku.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShizukuShellPackageInstallerTest {
    @Test
    fun parsesInstallSessionId() {
        assertEquals(123456, ShizukuShellPackageInstaller.parseSessionId("Success: created install session [123456]"))
    }

    @Test
    fun returnsNullWhenSessionMissing() {
        assertNull(ShizukuShellPackageInstaller.parseSessionId("Failure [INSTALL_FAILED]"))
    }
}
