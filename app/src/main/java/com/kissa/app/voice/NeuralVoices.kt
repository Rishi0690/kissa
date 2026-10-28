package com.kissa.app.voice

import com.kissa.app.model.BodyType
import com.kissa.app.model.CharacterSpec
import com.kissa.app.model.Lang

/** Gemini prebuilt voices (names per ai.google.dev speech-generation docs), used directly with the user's key or via the backend. */
data class NeuralVoice(val name: String, val trait: String, val feminine: Boolean)

object NeuralVoices {
    val ALL = listOf(
        NeuralVoice("Leda", "Youthful", true), NeuralVoice("Aoede", "Breezy", true), NeuralVoice("Kore", "Firm", true),
        NeuralVoice("Zephyr", "Bright", true), NeuralVoice("Autonoe", "Bright", true), NeuralVoice("Despina", "Smooth", true),
        NeuralVoice("Erinome", "Clear", true), NeuralVoice("Laomedeia", "Upbeat", true), NeuralVoice("Achernar", "Soft", true),
        NeuralVoice("Callirrhoe", "Easy-going", true), NeuralVoice("Vindemiatrix", "Gentle", true), NeuralVoice("Sulafat", "Warm", true),
        NeuralVoice("Pulcherrima", "Forward", true), NeuralVoice("Gacrux", "Mature", true),
        NeuralVoice("Puck", "Upbeat", false), NeuralVoice("Achird", "Friendly", false), NeuralVoice("Fenrir", "Excitable", false),
        NeuralVoice("Charon", "Informative", false), NeuralVoice("Orus", "Firm", false), NeuralVoice("Enceladus", "Breathy", false),
        NeuralVoice("Iapetus", "Clear", false), NeuralVoice("Umbriel", "Easy-going", false), NeuralVoice("Algieba", "Smooth", false),
        NeuralVoice("Zubenelgenubi", "Casual", false), NeuralVoice("Sadachbia", "Lively", false), NeuralVoice("Algenib", "Gravelly", false),
        NeuralVoice("Alnilam", "Firm", false), NeuralVoice("Schedar", "Even", false), NeuralVoice("Sadaltager", "Knowledgeable", false),
        NeuralVoice("Rasalgethi", "Informative", false),
    )

    fun isValid(name: String?) = name != null && ALL.any { it.name == name }

    fun playerDefault(body: BodyType) = if (body == BodyType.FEM) "Aoede" else "Puck"

    fun forPlayer(chosen: String?, body: BodyType) = chosen?.takeIf { isValid(it) } ?: playerDefault(body)

    fun forCharacter(c: CharacterSpec, override: String?): String =
        override?.takeIf { isValid(it) } ?: c.voice.neuralVoice?.takeIf { isValid(it) } ?: if (c.voice.feminine) "Leda" else "Achird"

    /** Fixed Microsoft neural voice for a character (secondary engine), distinct from the player's own voice. */
    fun edgeForCharacter(c: CharacterSpec, override: String?, playerEdge: String?, playerBody: BodyType) =
        com.kissa.app.voice.engines.EdgeTts.characterVoice(forCharacter(c, override), c.voice.feminine,
            com.kissa.app.voice.engines.EdgeTts.playerVoice(playerEdge, playerBody == BodyType.FEM).id)

    fun langCode(l: Lang) = when (l) { Lang.HINGLISH -> "hinglish"; Lang.ENGLISH -> "english"; Lang.HINDI -> "hindi" }
}
