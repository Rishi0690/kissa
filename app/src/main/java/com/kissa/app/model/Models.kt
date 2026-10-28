package com.kissa.app.model

import kotlinx.serialization.Serializable

/** Facial expression states the scene engine can render. The AI picks one per reply. */
@Serializable
enum class Emotion(val label: String) {
    NEUTRAL("Neutral"), HAPPY("Happy"), SHY("Shy"), SURPRISED("Surprised"),
    CONFUSED("Confused"), SAD("Sad"), ANGRY("Annoyed"), AMUSED("Amused");

    companion object {
        fun fromAny(raw: String?): Emotion? {
            val s = raw?.trim()?.lowercase()?.replace(' ', '_')?.replace('-', '_') ?: return null
            entries.firstOrNull { it.name.lowercase() == s }?.let { return it }
            return when (s) {
                "joy", "joyful", "excited", "cheerful", "glad", "smile", "smiling", "delighted", "warm", "playful_happy" -> HAPPY
                "embarrassed", "blush", "blushing", "flustered", "nervous", "bashful", "shy_happy" -> SHY
                "shocked", "astonished", "amazed", "startled", "wow" -> SURPRISED
                "puzzled", "perplexed", "unsure", "uncertain", "doubtful", "skeptical", "curious", "thinking", "thoughtful" -> CONFUSED
                "upset", "hurt", "sorrow", "unhappy", "disappointed", "melancholy", "lonely", "worried" -> SAD
                "annoyed", "mad", "irritated", "frustrated", "furious", "angry", "offended", "stern" -> ANGRY
                "teasing", "smirk", "smirking", "playful", "laughing", "entertained", "sarcastic", "mischievous" -> AMUSED
                "calm", "neutral", "normal", "relaxed", "serious", "focused" -> NEUTRAL
                else -> null
            }
        }
    }
}

/** Body-language poses the procedural rig can perform. */
@Serializable
enum class Gesture(val label: String) {
    NONE("Idle"), WAVE("Wave"), NOD("Nod"), SHAKE_HEAD("Shake head"), SHRUG("Shrug"),
    CROSS_ARMS("Cross arms"), HAND_ON_HIP("Hand on hip"), THINK_CHIN("Hand on chin"),
    POINT("Point"), COVER_FACE("Cover face"), LAUGH("Laugh"), FACEPALM("Facepalm"),
    HEAD_TILT("Head tilt"), STEP_CLOSER("Step closer"), STEP_BACK("Step back"), TURN_AWAY("Turn away");

    companion object {
        fun fromAny(raw: String?): Gesture? {
            val s = raw?.trim()?.lowercase()?.replace(' ', '_')?.replace('-', '_') ?: return null
            entries.firstOrNull { it.name.lowercase() == s }?.let { return it }
            return when (s) {
                "idle", "none", "", "stand", "standing" -> NONE
                "waving", "hello", "greet" -> WAVE
                "nodding", "agree" -> NOD
                "shaking_head", "no", "disagree", "head_shake" -> SHAKE_HEAD
                "arms_crossed", "crossed_arms", "fold_arms" -> CROSS_ARMS
                "hands_on_hips", "hip" -> HAND_ON_HIP
                "thinking", "chin", "hand_chin", "ponder" -> THINK_CHIN
                "pointing" -> POINT
                "hide_face", "cover", "covering_face", "hands_over_face" -> COVER_FACE
                "laughing", "giggle", "chuckle" -> LAUGH
                "tilt", "tilt_head", "head_tilt" -> HEAD_TILT
                "approach", "lean_in", "come_closer", "step_forward" -> STEP_CLOSER
                "back_away", "retreat", "step_away" -> STEP_BACK
                "look_away", "turn", "turns_away" -> TURN_AWAY
                else -> null
            }
        }
    }
}

/** Discrete environmental/story events the AI can trigger. */
@Serializable
enum class SceneEvent {
    NONE, LIGHT_FLICKER, CROWD_PASSES, WIND_GUST, RAIN_START, RAIN_STOP, LIGHTS_DIM, LIGHTS_BRIGHT,
    CHARACTER_EXITS, CHARACTER_ENTERS, CHAPTER_COMPLETE, PLOT_TWIST, ENDING;

    companion object {
        fun fromAny(raw: String?): SceneEvent? {
            val s = raw?.trim()?.uppercase()?.replace(' ', '_')?.replace('-', '_') ?: return null
            if (s.isEmpty()) return NONE
            entries.firstOrNull { it.name == s }?.let { return it }
            return when {
                s.contains("FLICKER") -> LIGHT_FLICKER
                s.contains("CROWD") || s.contains("STUDENTS") || s.contains("PASS") -> CROWD_PASSES
                s.contains("WIND") || s.contains("BREEZE") -> WIND_GUST
                s.contains("RAIN") && (s.contains("STOP") || s.contains("END")) -> RAIN_STOP
                s.contains("RAIN") -> RAIN_START
                s.contains("DIM") || s.contains("DARK") -> LIGHTS_DIM
                s.contains("BRIGHT") || s.contains("SUN") -> LIGHTS_BRIGHT
                s.contains("LEAVE") || s.contains("EXIT") || s.contains("WALKS_AWAY") -> CHARACTER_EXITS
                s.contains("RETURN") || s.contains("ENTER") || s.contains("COMES_BACK") -> CHARACTER_ENTERS
                s.contains("CHAPTER") -> CHAPTER_COMPLETE
                s.contains("TWIST") || s.contains("REVEAL") -> PLOT_TWIST
                s.contains("ENDING") || s == "END" -> ENDING
                else -> null
            }
        }
    }
}

@Serializable
enum class Lang(val label: String, val sttTag: String, val ttsTag: String) {
    HINGLISH("Hinglish", "hi-IN", "en-IN"),
    ENGLISH("English", "en-IN", "en-IN"),
    HINDI("हिन्दी (Hindi)", "hi-IN", "hi-IN");
}

@Serializable
enum class Genre(val label: String, val emoji: String) {
    SLICE_OF_LIFE("Slice of life", "☕"), COLLEGE("School & College", "🎓"), FRIENDSHIP("Friendship", "🤝"),
    MYSTERY("Mystery", "🔎"), FANTASY("Fantasy", "🌿"), SCI_FI("Sci-Fi", "🚀"), COMEDY("Comedy", "😂"),
    DRAMA("Drama", "🎭"), HISTORICAL("Historical", "🏛"), SUPERHERO("Superhero", "⚡"), QUEST("Quest", "🗺")
}

/** Background + ambient package. Each maps to a painted background and an ambient effect set. */
@Serializable
enum class SettingId(val label: String, val floor: Float) {
    CORRIDOR("College corridor", 0.90f), CAFE("Cosy café", 0.92f), LIBRARY("Old library at night", 0.92f),
    FOREST("Enchanted grove", 0.92f), SPACESHIP("Starship deck", 0.92f), STREET("Monsoon street", 0.92f),
    PARK("Sunset park", 0.93f), CLASSROOM("Classroom", 0.93f)
}

@Serializable enum class HairStyle { LONG, PONYTAIL, BOB, BUN, SHORT, SPIKY, CURLY }
@Serializable enum class OutfitStyle { HOODIE, BLAZER, KURTI, JACKET, DRESS, UNIFORM, TEE }
@Serializable enum class BodyType { FEM, MASC }
@Serializable enum class Accessory { NONE, GLASSES, EARRINGS, HEADBAND, ELF_EARS, VISOR }

@Serializable
data class Appearance(
    val body: BodyType = BodyType.FEM,
    val skin: Long = 0xFFF1C7A4,
    val hair: Long = 0xFF2A1B1F,
    val hairStyle: HairStyle = HairStyle.LONG,
    val eyes: Long = 0xFF5B3A29,
    val outfit: Long = 0xFF3D4E8A,
    val outfitAccent: Long = 0xFFF2EDE4,
    val outfitStyle: OutfitStyle = OutfitStyle.HOODIE,
    val accessory: Accessory = Accessory.NONE,
    val bottom: Long = 0xFF2B2F45,
)

@Serializable
data class VoiceSpec(
    val pitch: Float = 1.0f,
    val rate: Float = 1.0f,
    val voiceName: String? = null,
    val feminine: Boolean = true,
    /** Gemini prebuilt neural voice used through the Kissa backend (see NeuralVoices). Null = default by gender. */
    val neuralVoice: String? = null,
)

@Serializable
data class CharacterSpec(
    val id: String,
    val name: String,
    val age: Int = 20,
    val role: String,
    val appearance: Appearance,
    val background: String,
    val personality: String,
    val speakingStyle: String,
    val likes: String,
    val dislikes: String,
    val goals: String,
    val boundaries: String,
    val quirks: String = "",
    val voice: VoiceSpec = VoiceSpec(),
)

@Serializable
data class Chapter(val title: String, val goal: String)

@Serializable
data class Story(
    val id: String,
    val title: String,
    val tagline: String,
    val premise: String,
    val genres: List<Genre>,
    val setting: SettingId,
    val atmosphere: String,
    val openingSituation: String,
    val playerRole: String,
    val playerAppearance: Appearance,
    val character: CharacterSpec,
    val chapters: List<Chapter>,
    val language: Lang = Lang.HINGLISH,
    val startingRelationship: Int = 30,
    val startingMood: Int = 55,
    val isCustom: Boolean = false,
    val accent: Long = 0xFFFF7A90,
    val featured: Boolean = false,
    val playerNameOverride: String? = null,
)

enum class Role { PLAYER, CHARACTER, NARRATOR }
