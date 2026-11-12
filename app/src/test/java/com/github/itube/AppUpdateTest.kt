package com.github.itube

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {
    private fun fixture(name: String) = JSONObject(javaClass.classLoader!!.getResource(name)!!.readText())

    @Test
    fun latestReleaseParsed() {
        val release = AppUpdate.parseRelease(fixture("release_latest.json"))!!
        assertEquals("1.3.0", release.version)
        assertEquals("https://github.com/d0x-dev/AirStream/releases/download/v1.3.0/iTube_v1.3.0_signed.apk", release.apkUrl)
        assertEquals("https://github.com/d0x-dev/iTube/releases/tag/v1.3.0", release.releaseUrl)
        assertEquals(9696261L, release.size)
        assertEquals("160d649054110e5c39b4b59cb178db3cbe73ef015f324fdc45ef10b606b9a932", release.sha256)
    }

    @Test
    fun releaseWithoutTagIsNothing() {
        assertNull(AppUpdate.parseRelease(JSONObject().put("tag_name", "")))
    }

    @Test
    fun markdownParsed() {
        val blocks = AppUpdate.parseMarkdown(
            """
            ### New
            - Tap **Update** to see what's new.
            * `Shorts` reopen faster.

            A paragraph here.
            """.trimIndent()
        )
        assertTrue(blocks.any { it.kind == AppUpdate.MarkdownKind.Heading && it.text == "New" && it.level == 3 })
        assertTrue(blocks.any { it.kind == AppUpdate.MarkdownKind.Bullet && it.text.contains("Tap **Update**") })
        assertTrue(blocks.any { it.kind == AppUpdate.MarkdownKind.Bullet && it.text.contains("`Shorts`") })
        assertTrue(blocks.any { it.kind == AppUpdate.MarkdownKind.Paragraph && it.text == "A paragraph here." })
    }

    @Test
    fun releasesListGathersNotesSinceThisBuild() {
        fun release(version: String, body: String, prerelease: Boolean = false) = fixture("release_latest.json")
            .put("tag_name", "v$version")
            .put("html_url", "https://github.com/d0x-dev/iTube/releases/tag/v$version")
            .put("body", body)
            .put("prerelease", prerelease)

        val list = org.json.JSONArray()
            .put(release("0.5.70", "- beta", prerelease = true))
            .put(release("0.5.68", "### New\n- Sixty-eight"))
            .put(release("0.5.67", "- Sixty-seven"))
            .put(release("0.5.66", "Install AirStream.apk on Android."))
            .put(release("0.5.65", "- Already installed"))

        val latest = AppUpdate.parseReleases(list, current = "0.5.65")!!
        assertEquals("0.5.68", latest.version)
        assertEquals(listOf("0.5.68", "0.5.67", "0.5.66"), latest.notes.map { it.version })
    }

    @Test
    fun versionsCompareNumerically() {
        assertTrue(AppUpdate.compareVersions("0.5.55", "0.5.54") > 0)
        assertTrue(AppUpdate.compareVersions("0.5.100", "0.5.99") > 0)
        assertTrue(AppUpdate.compareVersions("0.6", "0.5.54") > 0)
        assertTrue(AppUpdate.compareVersions("0.5.54", "0.5.54") == 0)
        assertTrue(AppUpdate.compareVersions("0.5.9", "0.5.54") < 0)
    }
}
