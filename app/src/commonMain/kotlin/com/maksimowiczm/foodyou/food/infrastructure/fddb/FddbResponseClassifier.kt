package com.maksimowiczm.foodyou.food.infrastructure.fddb

import io.ktor.http.Url

internal enum class FddbPage { Diary, AuthenticationRequired, Unexpected }

/** A guest diary is HTTP 200 without a password field. Never infer success from an empty parser result. */
internal fun classifyFddbDiaryResponse(status: Int, url: Url, html: String): FddbPage {
    if (status == 401) return FddbPage.AuthenticationRequired
    if (status !in 200..299) return FddbPage.Unexpected
    if (url.host != "fddb.info") return FddbPage.Unexpected
    val text = html.replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ").replace(Regex("\\s+"), " ").lowercase()
    if (LoginField.containsMatchIn(html) || text.contains("melde dich mit deinen zugangsdaten an")) {
        return FddbPage.AuthenticationRequired
    }
    if (url.encodedPath.contains("/account/")) return FddbPage.AuthenticationRequired
    if (!url.encodedPath.contains("/notepad/")) return FddbPage.Unexpected
    return if (DiaryDate.containsMatchIn(html) || LogoutLink.containsMatchIn(html)) FddbPage.Diary
        else FddbPage.Unexpected
}

internal fun containsFddbLoginForm(html: String) = LoginField.containsMatchIn(html)

private val LoginField = Regex(
    """<input\b[^>]*\bname\s*=\s*(?:["']loginpassword["']|loginpassword(?=\s|>))""",
    RegexOption.IGNORE_CASE,
)
private val DiaryDate = Regex(
    """<td\b[^>]*\bclass\s*=\s*(?:"[^"]*\bnotepaddate\b[^"]*"|'[^']*\bnotepaddate\b[^']*'|notepaddate(?=\s|>))""",
    RegexOption.IGNORE_CASE,
)
private val LogoutLink = Regex(
    """<a\b[^>]*\bhref\s*=\s*["'][^"']*/(?:account|notepad)/\?[^"']*\baction=(?:logout|logoff)(?:&amp;|&|["'])""",
    RegexOption.IGNORE_CASE,
)

internal class FddbAuthenticationException : Exception("FDDB-Anmeldung fehlgeschlagen")
internal class FddbUnexpectedResponseException : Exception("FDDB hat keine gültige Tagebuchseite geliefert")
