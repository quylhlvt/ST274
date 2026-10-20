package com.oc.catemoji.catoc.ui.main.pause

import com.oc.catemoji.catoc.data.model.custom.CustomModel
import kotlin.random.Random

/** Navigation order is independent of the stacking order used to draw the character. */
class PauseChallengeSession(val template: CustomModel) {
    data class Choice(val colorIndex: Int, val pathIndex: Int, val path: String)
    data class Stage(val partIndex: Int, val nav: String, val choices: List<Choice>)

    val stages = template.listPath.mapIndexedNotNull { partIndex, part ->
        val choices = part.listPath.flatMapIndexed { colorIndex, color ->
            color.listPath.mapIndexedNotNull { pathIndex, path ->
                path.takeIf { it.isNotBlank() && it != "none" && it != "dice" }
                    ?.let { Choice(colorIndex, pathIndex, it) }
            }
        }
        choices.takeIf { it.isNotEmpty() }?.let { Stage(partIndex, part.nav, it) }
    }.sortedWith(compareBy<Stage> {
        // In real catalogues nav is an image URL. Never sort by a number in its host
        // or template folder; the catalogue's zIndex supplies its navigation order.
        val name = it.nav.substringAfterLast('/').substringBeforeLast('.')
        Regex("nav[_-]?(\\d+)", RegexOption.IGNORE_CASE).matchEntire(name)
            ?.groupValues?.get(1)?.toIntOrNull() ?: template.listPath[it.partIndex].zIndex
    }.thenBy { it.partIndex })

    var stageIndex = 0
        private set
    val currentStage: Stage? get() = stages.getOrNull(stageIndex)
    val isComplete: Boolean get() = stageIndex >= stages.size
    val selectedChoices = mutableMapOf<Int, Choice>()

    fun randomChoice(random: Random = Random.Default): Choice? {
        val stage = currentStage ?: return null
        val previous = selectedChoices[stage.partIndex]
        val different = stage.choices.filter { it != previous }
        return (different.ifEmpty { stage.choices }).random(random)
    }

    /** Commit only after the corresponding image has actually reached the screen. */
    fun show(choice: Choice) {
        val stage = currentStage ?: return
        require(choice in stage.choices)
        selectedChoices[stage.partIndex] = choice
    }

    fun advance(skip: Boolean = false): Boolean {
        val stage = currentStage ?: return false
        if (!skip && selectedChoices[stage.partIndex] == null) return false
        if (skip) selectedChoices.remove(stage.partIndex)
        stageIndex++
        return true
    }
}
