package com.maksimowiczm.foodyou.food.infrastructure.fddb

import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals

class FddbResponseClassifierTest {
    private val url = Url("https://fddb.info/db/i18n/notepad/")

    @Test
    fun recognizesGuest200WithoutLoginFormAndDifferentLoginFieldStyles() {
        assertEquals(FddbPage.AuthenticationRequired, classifyFddbDiaryResponse(200, url,
            "<div>Bereits bei Fddb registriert? <a>Melde Dich mit Deinen Zugangsdaten an</a></div>"))
        for (field in listOf("name='loginpassword'", "name=loginpassword", "name=\"LOGINPASSWORD\"")) {
            assertEquals(FddbPage.AuthenticationRequired,
                classifyFddbDiaryResponse(200, url, "<input type=password $field>"))
        }
        assertEquals(FddbPage.AuthenticationRequired,
            classifyFddbDiaryResponse(200, Url("https://fddb.info/db/i18n/account/?action=login"), "<html>Account</html>"))
    }

    @Test
    fun acceptsEmptyAuthenticatedDiaryButRejectsUnknownPagesAndErrors() {
        assertEquals(FddbPage.Diary, classifyFddbDiaryResponse(200, url,
            "<a href='/db/i18n/account/?lang=de&amp;action=logout'>Abmelden</a>"))
        assertEquals(FddbPage.Diary, classifyFddbDiaryResponse(200, url, "<td class='foo notepaddate bar'>Tag</td>"))
        assertEquals(FddbPage.Unexpected, classifyFddbDiaryResponse(200, url, "<html>Wartung</html>"))
        for (status in listOf(403, 429, 500)) {
            assertEquals(FddbPage.Unexpected, classifyFddbDiaryResponse(status, url, "<td class=notepaddate>Tag</td>"))
        }
        assertEquals(FddbPage.AuthenticationRequired, classifyFddbDiaryResponse(401, url, ""))
    }
}
