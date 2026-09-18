package com.maksimowiczm.foodyou.food.domain.entity

import kotlin.time.Instant

data class QuickCaptureFoodName(
    val id: Long,
    val name: String,
    val normalizedName: String,
    val usageCount: Long,
    val lastUsedAt: Instant,
)

enum class QuickCaptureWeightMode {
    Direct,
    BeforeAfter,
}

data class QuickCaptureLogEntry(
    val id: Long,
    val foodNameId: Long?,
    val foodName: String?,
    val weightMode: QuickCaptureWeightMode,
    val directWeightInGrams: Double?,
    val beforeWeightInGrams: Double?,
    val afterWeightInGrams: Double?,
    val photoPath: String?,
    val createdAt: Instant,
    val completedAt: Instant?,
) {
    val effectiveWeightInGrams: Double?
        get() =
            when (weightMode) {
                QuickCaptureWeightMode.Direct ->
                    directWeightInGrams?.takeIf { it.isFinite() && it > 0.0 }
                QuickCaptureWeightMode.BeforeAfter -> {
                    val before = beforeWeightInGrams
                    val after = afterWeightInGrams
                    if (
                        before != null &&
                            after != null &&
                            before.isFinite() &&
                            after.isFinite() &&
                            before > 0.0 &&
                            after >= 0.0 &&
                            before > after
                    ) {
                        before - after
                    } else {
                        null
                    }
                }
            }

    val isPendingPhoto: Boolean
        get() = photoPath != null && foodName.isNullOrBlank()

    val isAwaitingAfter: Boolean
        get() =
            weightMode == QuickCaptureWeightMode.BeforeAfter &&
                afterWeightInGrams == null

    val isReady: Boolean
        get() = !foodName.isNullOrBlank() && effectiveWeightInGrams != null

    val isCompleted: Boolean
        get() = completedAt != null
}

data class QuickCaptureLogGroup(
    val key: String,
    val foodName: String,
    val entries: List<QuickCaptureLogEntry>,
    val weightInGrams: Double,
)

fun normalizeQuickCaptureFoodName(input: String): String =
    input.trim().replace(Regex("\\s+"), " ").lowercase()

fun canonicalQuickCaptureFoodName(input: String): String =
    input.trim().replace(Regex("\\s+"), " ")

fun Iterable<QuickCaptureLogEntry>.quickCaptureGroups(
    aggregateSameFoods: Boolean,
): List<QuickCaptureLogGroup> {
    val ready = filter { !it.isCompleted && it.isReady }
    val grouped =
        if (aggregateSameFoods) {
            ready.groupBy { entry ->
                entry.foodNameId?.let { "library:$it" }
                    ?: "name:${normalizeQuickCaptureFoodName(requireNotNull(entry.foodName))}"
            }
        } else {
            ready.associateBy { "entry:${it.id}" }.mapValues { listOf(it.value) }
        }

    return grouped.map { (key, entries) ->
        QuickCaptureLogGroup(
            key = key,
            foodName = requireNotNull(entries.first().foodName),
            entries = entries,
            weightInGrams = entries.sumOf { requireNotNull(it.effectiveWeightInGrams) },
        )
    }
}

const val QuickCaptureChatGptPrompt =
    """Berechne mir die Kalorien und Makros für die nachfolgenden Lebensmittel.

Du sollst eher konservativ schätzen. Ich will das Ganze für eine Diät, also eher die höheren Kalorien nehmen, aber ohne zu übertreiben, also trotzdem realistisch bleiben.

Gleiche mit Food-Datenbanken ab, um genauere Angaben zu erhalten.

Gib ausschließlich CSV zurück.
Format exakt: name,energy,proteins,carbohydrates,fats
"Kurzer Mahlzeitenname",kcal,protein_g,kohlenhydrate_g,fett_g

Regeln:
- energy ist kcal.
- proteins, carbohydrates und fats sind Gramm.
- Verwende Punkt als Dezimaltrennzeichen, keine Einheiten.
- Der Name soll kurz zusammenfassen, was enthalten war.
- Wenn der Name Kommas oder Anführungszeichen enthält, nutze korrektes CSV-Quoting.
- Gib keinen Markdown-Codeblock und keinen erklärenden Text aus.
- Genau eine Header-Zeile und genau eine Datenzeile."""

fun Iterable<QuickCaptureLogGroup>.quickCapturePrompt(): String {
    val foods = joinToString(separator = "\n") { group ->
        "${group.weightInGrams.formatQuickCaptureWeight()}g ${group.foodName}"
    }
    return "$QuickCaptureChatGptPrompt\n\n$foods"
}

fun Double.formatQuickCaptureWeight(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()
