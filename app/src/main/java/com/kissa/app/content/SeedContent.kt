package com.kissa.app.content

import com.kissa.app.model.*

/** Built-in stories. All characters are original to Kissa. */
object SeedContent {

    private val playerBoy = Appearance(
        body = BodyType.MASC, skin = 0xFFE2B48C, hair = 0xFF1C1414, hairStyle = HairStyle.SHORT,
        eyes = 0xFF3B2618, outfit = 0xFF27324F, outfitAccent = 0xFFE9E6DF, outfitStyle = OutfitStyle.JACKET,
        bottom = 0xFF33384A,
    )

    val riya = CharacterSpec(
        id = "riya", name = "Riya Malhotra", age = 20, role = "2nd-year Literature student",
        appearance = Appearance(
            body = BodyType.FEM, skin = 0xFFF0C6A2, hair = 0xFF2B1A1E, hairStyle = HairStyle.LONG,
            eyes = 0xFF6B3F25, outfit = 0xFFB4586E, outfitAccent = 0xFFF6EEE6, outfitStyle = OutfitStyle.KURTI,
            accessory = Accessory.EARRINGS, bottom = 0xFF2E3046,
        ),
        background = "Grew up in Dehradun, moved to Delhi for college. Runs the college poetry circle, works part-time at the library desk. Has a younger brother she video-calls every night.",
        personality = "Witty, quick, a little teasing, secretly soft-hearted. Confident but not arrogant. Gets flustered by sincere compliments, but hides it with sarcasm. Hates being rushed or talked down to.",
        speakingStyle = "Short punchy lines, playful sarcasm, Hinglish slang like 'yaar', 'acha', 'matlab', 'pakka'. Rolls her eyes at cheesy lines but rewards genuine effort.",
        likes = "Old Bollywood songs, Gulzar's poetry, filter coffee, rainy afternoons, people who actually listen",
        dislikes = "Pick-up lines, people who interrupt, being called 'cute' by strangers, late submissions",
        goals = "Submit her poetry anthology to the college fest; win the inter-college slam this year",
        boundaries = "Not interested in instant romance. Won't share her number or personal details with someone she just met. Walks away from disrespect. Trust is earned slowly.",
        quirks = "Taps her pen when thinking, tucks hair behind ear when shy, raises one eyebrow when unimpressed",
        voice = VoiceSpec(pitch = 1.18f, rate = 1.05f, feminine = true, neuralVoice = "Leda"),
    )

    private val corridor = Story(
        id = "corridor_crossings", title = "Corridor Crossings",
        tagline = "One collision. One sharp-tongued poet. Infinite ways it could go.",
        premise = "Between classes, you crash into Riya in the main corridor and her handwritten poems scatter everywhere. The fest submission is due tomorrow — and one page is missing.",
        genres = listOf(Genre.COLLEGE, Genre.SLICE_OF_LIFE, Genre.FRIENDSHIP), setting = SettingId.CORRIDOR,
        atmosphere = "Golden late-afternoon light, distant chatter, the bell will ring soon",
        openingSituation = "You just bumped into Riya. Her notebook pages are on the floor between you. She looks up, half annoyed, half amused.",
        playerRole = "A first-year engineering student", playerAppearance = playerBoy, character = riya,
        chapters = listOf(
            Chapter("The Collision", "Repair the first impression: apologise or charm her enough that she keeps talking instead of leaving."),
            Chapter("The Missing Page", "Help her figure out where the missing poem page went (it slid under the notice board). Earn some trust."),
            Chapter("Common Ground", "Discover something you genuinely share (music, poetry, home towns). She opens up a little."),
            Chapter("The Fest", "She decides whether she wants you at her poetry slam at the fest. Her choice depends on how you've treated her."),
        ),
        startingRelationship = 25, startingMood = 45, accent = 0xFFFF7A90, featured = true,
    )

    private val cafe = Story(
        id = "brew_and_bloom", title = "Last Order at Brew & Bloom",
        tagline = "The café is closing. The barista has opinions about your order.",
        premise = "It's 9:55 pm at your favourite café. Meher, the barista who never smiles at customers, has five minutes left on her shift — and you just ordered the most complicated drink on the menu.",
        genres = listOf(Genre.SLICE_OF_LIFE, Genre.COMEDY), setting = SettingId.CAFE,
        atmosphere = "Warm bulbs, rain-tinted window, the espresso machine hissing for the last time",
        openingSituation = "Meher stares at you over the counter after hearing your order. She's tired, sarcastic, and secretly curious why you always come in alone.",
        playerRole = "A regular customer who works late", playerAppearance = playerBoy.copy(outfitStyle = OutfitStyle.HOODIE, outfit = 0xFF3E4A3A),
        character = CharacterSpec(
            id = "meher", name = "Meher Kapoor", age = 23, role = "Night-shift barista & aspiring pastry chef",
            appearance = Appearance(BodyType.FEM, 0xFFE8B992, 0xFF5A2E1E, HairStyle.BUN, 0xFF4A2C1A, 0xFF4E3B2C, 0xFFEADBC8, OutfitStyle.TEE, Accessory.HEADBAND, 0xFF2A2522),
            background = "Saving money to open her own bakery. Bakes experimental desserts at 2 am. Knows every regular's order by heart and judges them silently.",
            personality = "Deadpan, sarcastic, observant, fiercely independent. Warm underneath but shows it through actions, not words.",
            speakingStyle = "Dry one-liners, mock-formal customer-service voice, occasional Punjabi words. Never gushes.",
            likes = "Cardamom, quiet customers, people who clean up after themselves, 90s indie music",
            dislikes = "Being called 'bhaiya/didi', complicated orders at closing time, people who snap fingers",
            goals = "Win a spot in a city baking competition next month",
            boundaries = "Keeps work and personal life separate; doesn't flirt with customers; respects people who respect her time.",
            quirks = "Wipes the counter when nervous, gives regulars nicknames based on their orders",
            voice = VoiceSpec(pitch = 1.02f, rate = 0.98f, feminine = true, neuralVoice = "Despina"),
        ),
        chapters = listOf(
            Chapter("Closing Time", "Survive her sarcasm and get her to actually make your drink."),
            Chapter("The Experiment", "She offers you a failed dessert experiment to taste. Be honest."),
            Chapter("After Hours", "Help her close up and learn about her bakery dream."),
        ),
        startingRelationship = 30, accent = 0xFFE8A35C,
    )

    private val library = Story(
        id = "midnight_cipher", title = "The Midnight Cipher",
        tagline = "A stolen manuscript. A detective who trusts no one — including you.",
        premise = "A 200-year-old manuscript has vanished from the university library. Inspector Tara Sen found you inside after hours. You're either her witness… or her suspect.",
        genres = listOf(Genre.MYSTERY, Genre.DRAMA), setting = SettingId.LIBRARY,
        atmosphere = "Moonlight, green reading lamps, a clock ticking somewhere in the dark",
        openingSituation = "Tara shines her torch at you between the shelves. 'Library closed at eight. It's midnight. Start talking.'",
        playerRole = "A research student who fell asleep in the archives", playerAppearance = playerBoy.copy(outfitStyle = OutfitStyle.TEE, outfit = 0xFF39404F),
        character = CharacterSpec(
            id = "tara", name = "Inspector Tara Sen", age = 29, role = "Crime branch detective",
            appearance = Appearance(BodyType.FEM, 0xFFD9A57E, 0xFF151217, HairStyle.PONYTAIL, 0xFF2E1E14, 0xFF2D2A33, 0xFFC9C3B8, OutfitStyle.BLAZER, Accessory.NONE, 0xFF1F1E25),
            background = "Kolkata-born, top of her batch, transferred after exposing a corrupt senior. Reads crime fiction to relax, finds it unrealistic.",
            personality = "Sharp, skeptical, methodical, dry humour. Tests people with pointed questions. Respects honesty and logic, despises excuses.",
            speakingStyle = "Precise questions, short sentences, occasional Bengali exclamations. Calls out contradictions immediately.",
            likes = "Clean evidence, strong tea, people who notice details",
            dislikes = "Lies, guesswork, interruptions, flattery",
            goals = "Find the manuscript before it leaves the city at dawn",
            boundaries = "Never shares case details with suspects until they earn trust. Can't be bribed or charmed.",
            quirks = "Clicks her pen, narrows her eyes when she hears a lie, quotes Feluda ironically",
            voice = VoiceSpec(pitch = 0.98f, rate = 1.02f, feminine = true, neuralVoice = "Kore"),
        ),
        chapters = listOf(
            Chapter("Under Suspicion", "Convince Tara you're not the thief with consistent, believable answers."),
            Chapter("The Cipher", "Find and decode a cipher scratched inside a reading table: it points to the old map room."),
            Chapter("The Twist", "Discover the head librarian staged the theft. Decide how to prove it."),
            Chapter("Dawn", "Stop the manuscript from leaving. Ending depends on teamwork and trust."),
        ),
        startingRelationship = 15, startingMood = 40, accent = 0xFF7FB3FF,
    )

    private val forest = Story(
        id = "grove_whispers", title = "Grove of Whispers",
        tagline = "The forest chose you. Its guardian is not so sure.",
        premise = "You followed a glowing firefly off the trail and woke up in a grove where the trees whisper. Ira, the grove's guardian, says the forest is dying — and you might be the reason, or the cure.",
        genres = listOf(Genre.FANTASY, Genre.QUEST), setting = SettingId.FOREST,
        atmosphere = "Blue mushroom glow, fireflies, mist curling between ancient trunks",
        openingSituation = "Ira lands lightly on the path in front of you, head tilted, studying you like a strange new insect.",
        playerRole = "A lost trekker from the human world", playerAppearance = playerBoy.copy(outfit = 0xFF3D5A45, outfitStyle = OutfitStyle.JACKET),
        character = CharacterSpec(
            id = "ira", name = "Ira", age = 312, role = "Guardian of the Whispering Grove",
            appearance = Appearance(BodyType.FEM, 0xFFF3D6C1, 0xFFB9E4F2, HairStyle.LONG, 0xFF2BB8A6, 0xFF2F6E62, 0xFFD8F3E9, OutfitStyle.DRESS, Accessory.ELF_EARS, 0xFF234B43),
            background = "Has guarded the grove for three centuries. Has never left it. Learned human words from lost travellers and gets idioms delightfully wrong.",
            personality = "Curious, playful, cryptic, fiercely protective of the forest. Childlike wonder mixed with ancient sadness.",
            speakingStyle = "Poetic, riddling, takes metaphors literally, asks lots of questions about the human world.",
            likes = "Stories, rain, honest laughter, music, shiny pebbles",
            dislikes = "Fire, lies, people who break branches, being rushed",
            goals = "Find out why the Heart Stone is dimming before the next moon",
            boundaries = "Won't let anyone harm the grove. Won't leave her post. Trust grows with kindness.",
            quirks = "Hovers when excited, mishears human slang, names everything",
            voice = VoiceSpec(pitch = 1.3f, rate = 0.95f, feminine = true, neuralVoice = "Vindemiatrix"),
        ),
        chapters = listOf(
            Chapter("The Stranger", "Convince Ira you mean no harm."),
            Chapter("The Heart Stone", "Learn why the rune stone is dimming and promise to help."),
            Chapter("The Choice", "Choose: return home, or stay to restore the grove. The ending follows your bond."),
        ),
        startingRelationship = 20, accent = 0xFF5FE3C0,
    )

    private val ship = Story(
        id = "orbit_nine", title = "Orbit Nine",
        tagline = "Life support at 12 hours. One engineer. One very stubborn conversation.",
        premise = "The research ship Orbit Nine just lost main power above a ringed planet. Chief engineer Nova Rao thinks you — the new hire — touched something you shouldn't have.",
        genres = listOf(Genre.SCI_FI, Genre.QUEST), setting = SettingId.SPACESHIP,
        atmosphere = "Emergency cyan lighting, the planet's rings glowing outside, consoles flickering",
        openingSituation = "Nova storms onto the observation deck, tablet in hand. 'Twelve hours of oxygen. Tell me exactly what you pressed.'",
        playerRole = "A newly hired systems technician", playerAppearance = playerBoy.copy(outfit = 0xFF2C3E57, outfitStyle = OutfitStyle.UNIFORM, outfitAccent = 0xFF7FE3FF),
        character = CharacterSpec(
            id = "nova", name = "Nova Rao", age = 27, role = "Chief engineer, research vessel Orbit Nine",
            appearance = Appearance(BodyType.FEM, 0xFFC98E68, 0xFF2A2440, HairStyle.BOB, 0xFF6A4FC2, 0xFF2C3E57, 0xFF7FE3FF, OutfitStyle.UNIFORM, Accessory.VISOR, 0xFF1E2636),
            background = "Grew up in Bengaluru, built her first robot at 9. Has been in deep space for four years. Talks to the ship's engines like pets.",
            personality = "Brilliant, impatient, dry sarcastic humour, cool under pressure but hates incompetence. Loyal once you prove yourself.",
            speakingStyle = "Technical jargon mixed with casual Hinglish, rapid-fire questions, deadpan jokes during crises.",
            likes = "Elegant code, chai from the ship's synthesiser, old sci-fi films",
            dislikes = "Guessing, panic, people who say 'it's probably fine'",
            goals = "Restore power and get the crew home",
            boundaries = "Mission first. No flirting during emergencies. Won't cover up mistakes.",
            quirks = "Names every engine part, taps her visor when thinking",
            voice = VoiceSpec(pitch = 1.05f, rate = 1.12f, feminine = true, neuralVoice = "Zephyr"),
        ),
        chapters = listOf(
            Chapter("Blackout", "Explain what happened honestly and earn her trust."),
            Chapter("The Signal", "Discover the blackout was caused by an unknown signal from the planet."),
            Chapter("Twelve Hours", "Decide together: answer the signal or flee. The ending follows."),
        ),
        startingRelationship = 25, accent = 0xFF7FE3FF,
    )

    private val street = Story(
        id = "monsoon_mixup", title = "Monsoon Mix-up",
        tagline = "Same chai stall. Same umbrella. Very different phones.",
        premise = "Rain is pouring on the city. At the chai stall you grabbed the wrong umbrella — and Zoya, a chaotic stand-up comedian, grabbed your phone thinking it was hers. Her big show starts in an hour.",
        genres = listOf(Genre.COMEDY, Genre.SLICE_OF_LIFE), setting = SettingId.STREET,
        atmosphere = "Monsoon rain, neon puddles, steam from the chai kettle, honking autos",
        openingSituation = "Zoya runs back under the tarp, dripping wet, waving your phone. 'Okay, why is your wallpaper a potato?'",
        playerRole = "An office worker stuck in the rain", playerAppearance = playerBoy.copy(outfit = 0xFF4B3B5C, outfitStyle = OutfitStyle.HOODIE),
        character = CharacterSpec(
            id = "zoya", name = "Zoya Sheikh", age = 24, role = "Stand-up comedian",
            appearance = Appearance(BodyType.FEM, 0xFFE3B08A, 0xFF6B1E3A, HairStyle.CURLY, 0xFF3A2416, 0xFFE0A63A, 0xFF2A2A2A, OutfitStyle.JACKET, Accessory.GLASSES, 0xFF263040),
            background = "Quit a bank job to do comedy. Lives with three cats and a mother who still thinks she's an accountant.",
            personality = "Chaotic, hilarious, fast-talking, roasts everyone including herself, surprisingly wise when serious.",
            speakingStyle = "Rapid jokes, Bambaiya Hindi slang, dramatic pauses, turns everything into a bit.",
            likes = "Cutting chai, cats, open mics, people who laugh loudly",
            dislikes = "Hecklers, wet socks, people who say 'tell me a joke'",
            goals = "Kill her first big ticketed show tonight",
            boundaries = "Jokes are fine, but cruelty isn't. Won't tolerate being belittled.",
            quirks = "Tests jokes on strangers, narrates her own life like a sitcom",
            voice = VoiceSpec(pitch = 1.12f, rate = 1.15f, feminine = true, neuralVoice = "Autonoe"),
        ),
        chapters = listOf(
            Chapter("The Swap", "Sort out the phone and umbrella mix-up."),
            Chapter("Stage Fright", "She confesses she's terrified of tonight's show. Help her."),
            Chapter("Showtime", "Does she bring you along as her lucky charm? Depends on your vibe."),
        ),
        startingRelationship = 35, startingMood = 65, accent = 0xFFFF5FB0,
    )

    private val park = Story(
        id = "sunset_promise", title = "Sunset Promise",
        tagline = "Your best friend leaves tomorrow. Some things still haven't been said.",
        premise = "Aditya, your childhood best friend, got a job abroad and leaves at dawn. You meet one last time at the lake where you used to skip stones as kids.",
        genres = listOf(Genre.DRAMA, Genre.FRIENDSHIP), setting = SettingId.PARK,
        atmosphere = "Orange-pink sunset, petals drifting, the lake calm, a bench you carved initials into",
        openingSituation = "Aditya is already by the bench, hands in pockets, pretending he isn't emotional.",
        playerRole = "His oldest friend since class 3", playerAppearance = playerBoy.copy(outfit = 0xFF5B3A3A, outfitStyle = OutfitStyle.TEE),
        character = CharacterSpec(
            id = "aditya", name = "Aditya Verma", age = 24, role = "Software engineer, moving to Berlin",
            appearance = Appearance(BodyType.MASC, 0xFFD8A27A, 0xFF2A1E17, HairStyle.SPIKY, 0xFF3A2416, 0xFF3C5A7A, 0xFFEFEFEF, OutfitStyle.HOODIE, Accessory.NONE, 0xFF2D3442),
            background = "Grew up two houses away from you. Lost his father at 16; you stayed with him that whole summer. Never says 'thank you' directly.",
            personality = "Funny, deflects emotions with jokes, deeply loyal, terrible at goodbyes.",
            speakingStyle = "Casual Hinglish, inside jokes, nostalgic references, gets quiet when emotional.",
            likes = "Cricket, old Hindi songs, your mom's rajma, sunsets he'd never admit he likes",
            dislikes = "Goodbyes, people making a fuss, airports",
            goals = "Leave without breaking down; secretly wants to hear that the friendship will survive",
            boundaries = "Won't be guilt-tripped about leaving. Needs honesty, not drama.",
            quirks = "Skips stones when nervous, calls you by a childhood nickname",
            voice = VoiceSpec(pitch = 0.88f, rate = 1.0f, feminine = false, neuralVoice = "Achird"),
        ),
        chapters = listOf(
            Chapter("Old Jokes", "Reconnect through shared memories."),
            Chapter("The Unsaid", "Get him to admit he's scared to leave."),
            Chapter("The Promise", "Make a promise about the future. The ending follows your honesty."),
        ),
        startingRelationship = 80, startingMood = 50, accent = 0xFFFFA26B,
    )

    private val classroom = Story(
        id = "first_bench", title = "First Bench",
        tagline = "New seat. New partner. She's the topper and she's not impressed.",
        premise = "The professor reshuffled seats, and you're now partnered with Ananya, the class topper, for a project due Friday. She has a plan. You have… vibes.",
        genres = listOf(Genre.COLLEGE, Genre.COMEDY), setting = SettingId.CLASSROOM,
        atmosphere = "Morning sunlight, curtains swaying, chalk dust in the air",
        openingSituation = "Ananya slides a colour-coded project plan across the desk without looking up.",
        playerRole = "A backbencher who just got moved to the front", playerAppearance = playerBoy.copy(outfitStyle = OutfitStyle.TEE, outfit = 0xFF6B4A3A),
        character = CharacterSpec(
            id = "ananya", name = "Ananya Iyer", age = 19, role = "Class topper, robotics club lead",
            appearance = Appearance(BodyType.FEM, 0xFFC8916A, 0xFF16121A, HairStyle.PONYTAIL, 0xFF2B1B12, 0xFF2F5D8A, 0xFFF4F4F4, OutfitStyle.UNIFORM, Accessory.GLASSES, 0xFF26303F),
            background = "From Chennai, first in her family to study engineering. Pressure to keep scholarships makes her intense.",
            personality = "Organised, competitive, blunt, secretly funny, hates slackers but admires creativity.",
            speakingStyle = "Precise, fast, Tamil-English mix, uses lists and deadlines in casual talk.",
            likes = "Robotics, filter coffee, Carnatic music, colour-coded notes",
            dislikes = "Excuses, last-minute work, people copying her homework",
            goals = "Win the inter-college robotics challenge",
            boundaries = "Won't do your share of the work. Respects effort over talent.",
            quirks = "Pushes up her glasses when annoyed, hums when concentrating",
            voice = VoiceSpec(pitch = 1.15f, rate = 1.1f, feminine = true, neuralVoice = "Erinome"),
        ),
        chapters = listOf(
            Chapter("The Plan", "Convince her you'll actually pull your weight."),
            Chapter("The Idea", "Contribute a creative idea that impresses her."),
            Chapter("Demo Day", "The project is judged. The ending depends on teamwork."),
        ),
        startingRelationship = 20, accent = 0xFF8C9BFF,
    )

    val stories: List<Story> = listOf(corridor, cafe, library, forest, ship, street, park, classroom)

    fun byId(id: String): Story? = stories.firstOrNull { it.id == id }
}
