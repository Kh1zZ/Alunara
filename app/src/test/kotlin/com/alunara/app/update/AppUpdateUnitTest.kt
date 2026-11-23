package com.alunara.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateUnitTest {
    @Test
    fun versionComparator_handlesSemVerPrefixesMultiDigitAndPrereleases() {
        assertTrue(AppVersionComparator.isNewer("v0.1.6", "0.1.5"))
        assertTrue(AppVersionComparator.isNewer("0.1.10", "0.1.9"))
        assertTrue(AppVersionComparator.isNewer("0.2.0", "0.1.9"))
        assertTrue(AppVersionComparator.isNewer("0.1.6", "0.1.6-beta.1"))

        assertFalse(AppVersionComparator.isNewer("0.1.5", "0.1.5"))
        assertFalse(AppVersionComparator.isNewer("0.1.4", "0.1.5"))
        assertFalse(AppVersionComparator.isNewer("v0.1.6-beta.1", "0.1.6"))
        assertFalse(AppVersionComparator.isNewer("invalid", "0.1.5"))
        assertNull(AppVersionComparator.parse(""))
        assertNull(AppVersionComparator.parse("1"))
        assertNull(AppVersionComparator.parse("0.1.6-"))
    }

    @Test
    fun assetSelector_picksExactAbiAndRejectsUntrustedUrls() {
        val v8Asset =
            GitHubReleaseAsset(
                name = "alunara-0.1.6.armv8a.apk",
                downloadUrl = "https://github.com/Kh1zZ/Alunara/releases/download/v0.1.6/alunara-0.1.6.armv8a.apk",
                sizeBytes = 22_000_000L,
            )
        val v7Asset =
            GitHubReleaseAsset(
                name = "alunara-0.1.6.armv7a.apk",
                downloadUrl = "https://objects.githubusercontent.com/alunara-0.1.6.armv7a.apk",
                sizeBytes = 21_500_000L,
            )
        val assets = listOf(v7Asset, v8Asset)

        assertEquals(v8Asset, ReleaseApkAssetSelector.selectBestAsset(assets, arrayOf("arm64-v8a", "armeabi-v7a")))
        assertEquals(v7Asset, ReleaseApkAssetSelector.selectBestAsset(assets, arrayOf("armeabi-v7a", "armeabi")))

        assertTrue(ReleaseApkAssetSelector.isTrustedDownloadUrl("https://github.com/Kh1zZ/Alunara/releases/download/v0.1.6/app.apk"))
        assertTrue(ReleaseApkAssetSelector.isTrustedDownloadUrl("https://release-assets.githubusercontent.com/some/file.apk"))
        assertFalse(ReleaseApkAssetSelector.isTrustedDownloadUrl("http://github.com/Kh1zZ/Alunara/app.apk"))
        assertFalse(ReleaseApkAssetSelector.isTrustedDownloadUrl("https://evilgithub.com/app.apk"))

        val untrustedOnly =
            listOf(
                v8Asset.copy(downloadUrl = "https://evil.example.com/alunara-0.1.6.armv8a.apk"),
            )
        assertNull(ReleaseApkAssetSelector.selectBestAsset(untrustedOnly, arrayOf("arm64-v8a")))
    }

    @Test
    fun parseReleaseJson_skipsDraftsAndPrereleasesAndStripsFooter() {
        val stableJson =
            """
            {
              "tag_name": "v0.1.6",
              "html_url": "https://github.com/Kh1zZ/Alunara/releases/tag/v0.1.6",
              "draft": false,
              "prerelease": false,
              "body": "## Alunara v0.1.6\n\n### App Updates\n- Add auto-updater using `FileProvider`.\n\n---\n### Official APK Downloads\n- Ignore footer",
              "assets": [
                {
                  "name": "alunara-0.1.6.armv7a.apk",
                  "browser_download_url": "https://github.com/Kh1zZ/Alunara/releases/download/v0.1.6/alunara-0.1.6.armv7a.apk",
                  "size": 21970393
                },
                {
                  "name": "alunara-0.1.6.armv8a.apk",
                  "browser_download_url": "https://github.com/Kh1zZ/Alunara/releases/download/v0.1.6/alunara-0.1.6.armv8a.apk",
                  "size": 21973090
                }
              ]
            }
            """.trimIndent()

        val parsed = GitHubReleaseClient.parseReleaseJson(stableJson, arrayOf("arm64-v8a"))
        assertNotNull(parsed)
        assertEquals("0.1.6", parsed!!.versionName)
        assertEquals("v0.1.6", parsed.tagName)
        assertEquals("alunara-0.1.6.armv8a.apk", parsed.asset.name)
        assertEquals(21973090L, parsed.asset.sizeBytes)
        assertTrue(parsed.releaseNotes.contains("• Add auto-updater using FileProvider."))
        assertFalse(parsed.releaseNotes.contains("Official APK Downloads"))

        val prereleaseJson = stableJson.replace("\"prerelease\": false", "\"prerelease\": true")
        assertNull(GitHubReleaseClient.parseReleaseJson(prereleaseJson, arrayOf("arm64-v8a")))

        val draftJson = stableJson.replace("\"draft\": false", "\"draft\": true")
        assertNull(GitHubReleaseClient.parseReleaseJson(draftJson, arrayOf("arm64-v8a")))
    }

    @Test
    fun parseReleaseFromRedirectUrl_buildsDeterministicSplitUrls() {
        val parsed64 =
            GitHubReleaseClient.parseReleaseFromRedirectUrl(
                "https://github.com/Kh1zZ/Alunara/releases/tag/v0.1.6",
                arrayOf("arm64-v8a"),
            )
        assertNotNull(parsed64)
        assertEquals("0.1.6", parsed64!!.versionName)
        assertEquals(
            "https://github.com/Kh1zZ/Alunara/releases/download/v0.1.6/alunara-0.1.6.armv8a.apk",
            parsed64.asset.downloadUrl,
        )

        val parsed32 =
            GitHubReleaseClient.parseReleaseFromRedirectUrl(
                "https://github.com/Kh1zZ/Alunara/releases/tag/v0.1.6",
                arrayOf("armeabi-v7a"),
            )
        assertNotNull(parsed32)
        assertEquals(
            "https://github.com/Kh1zZ/Alunara/releases/download/v0.1.6/alunara-0.1.6.armv7a.apk",
            parsed32!!.asset.downloadUrl,
        )

        assertNull(
            GitHubReleaseClient.parseReleaseFromRedirectUrl(
                "https://github.com/Kh1zZ/Alunara/releases",
                arrayOf("arm64-v8a"),
            ),
        )
    }

    @Test
    fun formatByteSize_formatsUnitsCleanly() {
        assertEquals("0 B", formatByteSize(0L))
        assertEquals("512 B", formatByteSize(512L))
        assertEquals("4 KB", formatByteSize(4096L))
        assertEquals("21.0 MB", formatByteSize(21L * 1024L * 1024L))
    }
}
