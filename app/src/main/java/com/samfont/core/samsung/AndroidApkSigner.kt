package com.samfont.core.samsung

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.android.apksig.ApkSigner
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Date
import javax.security.auth.x500.X500Principal

class AndroidApkSigner(
    private val alias: String = "samfont_generated_apk_signer"
) : com.samfont.core.samsung.ApkSigner {
    override fun sign(unsignedApk: File, signedApk: File) {
        val entry = loadOrCreateKey()
        val signerConfig = ApkSigner.SignerConfig.Builder(
            "samfont",
            entry.privateKey,
            listOf(entry.certificate)
        )
            .build()

        ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(unsignedApk)
            .setOutputApk(signedApk)
            .setMinSdkVersion(23)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(false)
            .setV4SigningEnabled(false)
            .build()
            .sign()
    }

    private fun loadOrCreateKey(): SigningEntry {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(alias)) {
            val generator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                "AndroidKeyStore"
            )
            val now = Date()
            val end = Date(now.time + 20L * 365L * 24L * 60L * 60L * 1000L)
            val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setCertificateSubject(X500Principal("CN=SamFont Generated"))
                .setCertificateSerialNumber(BigInteger.ONE)
                .setCertificateNotBefore(now)
                .setCertificateNotAfter(end)
                .build()
            generator.initialize(spec)
            generator.generateKeyPair()
        }

        val privateKey = keyStore.getKey(alias, null) as PrivateKey
        val certificate = keyStore.getCertificate(alias) as X509Certificate
        return SigningEntry(privateKey, certificate)
    }

    private data class SigningEntry(
        val privateKey: PrivateKey,
        val certificate: X509Certificate
    )
}
