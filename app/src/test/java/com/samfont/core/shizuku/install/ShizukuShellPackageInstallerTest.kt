package com.samfont.core.shizuku.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuShellPackageInstallerTest {
    @Test
    fun parsesInstallSessionId() {
        assertEquals(123456, ShizukuShellPackageInstaller.parseSessionId("Success: created install session [123456]"))
        assertEquals(42, ShizukuShellPackageInstaller.parseSessionId("stderr text\nSuccess: created install session [42]"))
        assertNull(ShizukuShellPackageInstaller.parseSessionId("Failure [INSTALL_FAILED]"))
    }

    @Test
    fun detectsSignatureMismatchRetryCases() {
        assertTrue(ShizukuShellPackageInstaller.shouldRetryAfterSignatureMismatch("INSTALL_FAILED_UPDATE_INCOMPATIBLE"))
        assertTrue(ShizukuShellPackageInstaller.shouldRetryAfterSignatureMismatch("signatures do not match"))
        assertTrue(ShizukuShellPackageInstaller.shouldRetryAfterSignatureMismatch("UPDATE_INCOMPATIBLE"))
        assertTrue(ShizukuShellPackageInstaller.shouldRetryAfterSignatureMismatch("existing package"))
        assertFalse(ShizukuShellPackageInstaller.shouldRetryAfterSignatureMismatch("INSTALL_FAILED_INVALID_APK"))
    }
}
