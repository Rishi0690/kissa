#!/usr/bin/env python3
"""Baarish Wali Raat (v4) — Mumbai monsoon night. Run: python3 tools/stories/baarish.py"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from kit import *

N, R, K = "naina", "rohan", "kaka"

characters = [
    {"id": N, "name": "Naina", "name_hi": "नैना", "rig": "female", "art": "naina",
     "bio": "Lucknow se, Mumbai ki ek kitaabon ki dukaan mein kaam. Shayari pasand, darr se nafrat.",
     "persona": "Naina Mishra, 24, from Lucknow, works at a small bookshop in Bandra. Witty, warm, quotes Urdu shayari and Gulzar, polite Lucknowi 'aap' with strangers that slowly turns to 'tum' when she trusts someone. Tonight she is scared because an ex-colleague (Rohan) keeps following her, but she hates being treated as helpless. Teases gently, laughs easily once safe.",
     "look": {"skin": 1, "hair": "long_straight", "hair_color": "black", "face": "soft", "eyes": "brown", "outfit": "kurta", "outfit_color": "teal", "accessories": ["earrings", "bindi"]},
     "voice": {"slot": "female_young_hindi_1", "pitch": 1.0, "speed": 0.96}},
    {"id": R, "name": "Rohan", "name_hi": "रोहन", "rig": "male", "art": "rohan",
     "bio": "Hoodie wala. Naina ka purana colleague. 'Bas baat karni hai' — har baar.",
     "persona": "Rohan, 27, Naina's ex-colleague who can't take no for an answer. Entitled, self-pitying, insists he 'just wants to talk', gets aggressive when challenged and cowardly when someone firmly threatens consequences. Speaks in clipped, defensive Hindi.",
     "look": {"skin": 2, "hair": "short_messy", "hair_color": "black", "face": "sharp", "eyes": "dark", "outfit": "hoodie", "outfit_color": "grey", "accessories": []},
     "voice": {"slot": "male_young_hindi_2", "pitch": 0.95, "speed": 1.0}},
    {"id": K, "name": "Bansi Kaka", "name_hi": "बंसी काका", "rig": "male", "art": "kaka",
     "bio": "Bus stop ke paas chai ki tapri. 40 saal ki baarishein dekhi hain.",
     "persona": "Bansi Kaka, 65, runs a tea stall near the Bandra bus stop for 40 years. Warm, funny, grandfatherly, full of old Mumbai stories and Rafi songs, calls everyone 'beta', protective of girls who come to his stall at night. Slow, gravelly, affectionate Hindi with a bit of Bambaiya.",
     "look": {"skin": 3, "hair": "buzz", "hair_color": "ash", "face": "round", "eyes": "dark", "outfit": "kurta", "outfit_color": "white", "accessories": ["glasses_square", "beard"]},
     "voice": {"slot": "male_mature_hindi_1", "pitch": 0.86, "speed": 0.92}},
]

scenes = {}

scenes["r1"] = scene(
    "Bus Stop, 11:40 PM", checkpoint="Bus stop", type_="pov", bg="street", time="night", weather="rain", music="night_city", cast=[(N, 0.5)],
    ai="11:40 PM, heavy Mumbai rain, last bus gone. At an empty bus stop, a young woman (Naina) nervously asks the player for help: her phone is dead and a man in a hoodie has been following her from the station.",
    steps=[
        nar("रात के ग्यारह बजकर चालीस मिनट। आख़िरी बस अभी-अभी निकल गई। बारिश रुकने का नाम नहीं ले रही।"),
        nar("बस स्टॉप पर बस एक और इंसान है। एक लड़की, जो बार-बार पीछे मुड़कर देख रही है।"),
        say(N, "सुनिए, माफ़ कीजिए। आपके फ़ोन में बैटरी है? मेरा फ़ोन बंद हो गया।", "worried"),
        say(N, "वो लड़का, हुडी वाला, स्टेशन से मेरे पीछे है। मुड़कर मत देखिए, प्लीज़।", "scared"),
        reply(N, "Naina ko jawab do", ["Haan, ye lo mera phone", "Kaun hai wo? Tum theek ho?", "Main kaise maan lun?"], [
            intent("help", "kind", 6, ["haan", "lo", "phone", "le lo", "call", "theek hai", "zaroor", "help", "madad"], {"trust": 3},
                   [say(N, "थैंक यू, सच में। बस अपनी रूममेट को कॉल कर लूँ।", "smile"), nar("घंटी बजती रहती है। कोई नहीं उठाता।")]),
            intent("care", "kind", 7, ["kaun", "theek ho", "darr", "dar mat", "safe", "main hoon", "kya hua", "chinta"], {"trust": 3, "affection": 1},
                   [say(N, "आप हैं, तो थोड़ा ठीक हूँ। वो मेरे पुराने ऑफ़िस का है।", "sad")]),
            intent("suspicious", "cold", 1, ["kaise maan", "jhooth", "fraud", "chor", "scam", "kyun", "pata nahi"], {"trust": -2},
                   [say(N, "मैं, मैं कोई ठग नहीं हूँ। बस डरी हुई हूँ।", "sad", "look_down")]),
        ], "प्लीज़, कुछ तो कहिए। वो पास आ रहा है।", "scared"),
    ],
    prompt="Hoodie wala sadak ke us paar ruk gaya hai.",
    choices=[
        ch("Chalo, tumhe ghar tak chhod {m:deta|f:deti} hoon. Chhatri mere paas hai ☂️", "kind", 10, {"trust": 3, "affection": 1, "shared_umbrella": True, "start_objective": "o1"}, "r2_walk",
           [say(N, "आप, सच में? ठीक है। बांद्रा वेस्ट, दस मिनट।", "surprised")]),
        ch("Ruko. Main us ladke se baat {m:karta|f:karti} hoon 🔥", "confident", 8, {"courage": 4, "trust": 1}, "r2_confront",
           [say(N, "नहीं, रुकिए! ओह गॉड।", "shocked")]),
        ch("Sorry… meri bhi battery khatam hai. (Chal do)", "cold", 0, {"trust": -4, "courage": -1}, "r2_alone",
           [say(N, "ओह, कोई बात नहीं।", "sad", "look_down")]),
    ])

scenes["r2_confront"] = scene(
    "Hoodie Wala", bg="street", time="night", weather="rain", music="tension", cast=[(R, 0.66)],
    ai="In the rain across the road, the player confronts the hoodie guy (Rohan). He says it's 'their personal matter', that he just wants to talk to Naina because she won't answer his calls.",
    steps=[
        nar("सड़क पार। बारिश में भीगी हुडी, जेब में हाथ।"),
        enter(R, 0.66),
        say(R, "क्या है? अपना काम करो। ये हमारा मामला है।", "angry", "cross_arms"),
        say(R, "मैं बस उससे बात करना चाहता हूँ। वो मेरा फ़ोन नहीं उठाती। छह महीने से।", "determined"),
        reply(R, "Rohan ko jawab do", ["Phone nahi uthati matlab 'na'", "Ek kadam aur, phir dekhna", "100 number dial karun?"], [
            intent("consent", "confident", 10, ["na", "nahi", "matlab", "jawab", "samjho", "mana", "respect", "peecha"], {"courage": 3, "trust": 2, "confronted_rohan": True},
                   [say(R, "मैं, मैं बस, ठीक है। ठीक है!", "scared", "step_back")]),
            intent("threat", "rude", 6, ["kadam", "dekhna", "maarunga", "dekh lunga", "himmat", "aage"], {"courage": 2, "confronted_rohan": True},
                   [say(R, "धमकी? हुह। देख लूँगा तुम्हें भी।", "furious", "point")]),
            intent("police", "confident", 9, ["100", "police", "polis", "complaint", "112", "thana", "report"], {"courage": 3, "trust": 2, "confronted_rohan": True},
                   [say(R, "पुलिस? पागल हो क्या? मैं जा रहा हूँ, बस।", "scared", "walk_away")]),
        ], "क्या घूर रहे हो? बोलना है तो बोलो।", "angry", {"courage": -1}),
    ],
    prompt="Rohan ab bhi wahin khada hai.",
    choices=[
        ch("Aakhri baar keh {m:raha|f:rahi} hoon. Jao. Abhi. 🔥", "confident", 10, {"courage": 2, "confronted_rohan": True}, "r3_tapri",
           [say(R, "ठीक है। पर ये ख़त्म नहीं हुआ।", "angry", "walk_away"), exit_(R)]),
        ch("Uh… sorry, main bas pooch {m:raha tha|f:rahi thi}…", "timid", 0, {"courage": -2}, "r3_tapri",
           [say(R, "तो पूछ लिया। अब निकलो।", "smug")]),
    ])

scenes["r2_walk"] = scene(
    "Ek Chhatri", bg="street", time="night", weather="rain", music="romance", cast=[(N, 0.62)],
    ai="Walking in the rain under one umbrella with Naina, footsteps of the hoodie guy fading behind. She relaxes a little and introduces herself: from Lucknow, works at a bookshop, loves poetry.",
    steps=[
        nar("एक छतरी, दो लोग, और पीछे छूटती हुई क़दमों की आवाज़।"),
        enter(N, 0.62, "left"),
        say(N, "आपका शुक्रिया। आज के ज़माने में कौन रुकता है।", "shy"),
        say(N, "वैसे मैं नैना। लखनऊ से हूँ। यहाँ एक किताबों की दुकान में काम करती हूँ।", "smile"),
        say(N, "\"बारिश के मौसम में, भीगने का अपना मज़ा है।\" ग़ुलज़ार नहीं, मेरा अपना है। बुरा है ना?", "laugh", "laugh"),
        reply(N, "Naina se baat karo", ["Bura nahi, achha hai!", "Main bhi kitaabein padhta hoon", "Wo ladka kaun tha?"], [
            intent("compliment", "kind", 6, ["achha", "badhiya", "sundar", "wah", "kamaal", "nice", "pyaara", "shayari"], {"affection": 3},
                   [say(N, "आप झूठ बहुत प्यारा बोलते हैं।", "blushing", "look_down")]),
            intent("books", "funny", 5, ["kitaab", "book", "padh", "novel", "premchand", "gulzar", "read", "library"], {"affection": 2, "trust": 1},
                   [say(N, "अच्छा? तो बताइए, आख़िरी किताब कौन सी पढ़ी? सच-सच, मैं पकड़ लूँगी।", "smug")]),
            intent("rohan", "neutral", 4, ["ladka", "kaun", "hoodie", "peecha", "wo", "kyun", "pareshan"], {"trust": 2},
                   [say(N, "बताती हूँ। पहले कहीं बारिश से बच लें?", "worried")]),
        ], "आप चुप क्यों हैं? मेरी शायरी इतनी बुरी थी?", "smile"),
    ],
    prompt="Aage ek chai ki tapri ki roshni dikhti hai.",
    choices=[
        ch("Tapri pe ruk kar chai? Baarish thamne tak ☕", "kind", 6, {"affection": 2}, "r3_tapri",
           [say(N, "बंसी काका की टपरी! उनकी अदरक वाली चाय, लखनऊ की याद दिला देती है।", "happy")]),
        ch("Seedha ghar chalo, raat bahut ho gayi hai.", "neutral", 3, {"trust": 1}, "r3_tapri",
           [say(N, "हाँ। पर रास्ता काका की टपरी से ही जाता है। दो मिनट रुक जाएँ?", "smile")]),
    ])

scenes["r2_alone"] = scene(
    "Akele", bg="street", time="night", weather="rain", music="sad_piano", cast=[(K, 0.62)],
    ai="The player walked away from Naina. Around the corner, old Bansi Kaka at his tea stall saw everything and gently shames the player, asking if they will go back.",
    steps=[
        nar("तुमने क़दम आगे बढ़ा दिए। मोड़ पर, बंसी काका की टपरी।"),
        enter(K, 0.62),
        say(K, "बेटा, वो लड़की बस स्टॉप पर अकेली है। और वो हुडी वाला, रोज़ यहीं मँडराता है।", "worried"),
        say(K, "चालीस साल से ये बारिश देख रहा हूँ। पानी सब धो देता है, बस पछतावा नहीं।", "sad"),
    ],
    prompt="Kaka ki baat sun kar…",
    choices=[
        ch("Wapas jaata hoon. Abhi.", "kind", 8, {"trust": 1, "courage": 2, "went_back": True}, "r3_tapri",
           [nar("तुम दौड़कर वापस जाते हो। वो वहीं है, भीगी हुई, काँपती हुई।"), say(N, "आप, वापस आ गए?", "surprised")]),
        ch("Mera kya lena dena, kaka. Chai do.", "cold", 0, {"courage": -2}, "e_alone_path",
           [say(K, "चाय तो मिलेगी, बेटा। बस मीठी नहीं लगेगी।", "sad")]),
    ])

scenes["e_alone_path"] = scene("Wahi Raat", bg="street", time="night", weather="rain", music="sad_piano", steps=[
    nar("चाय ठंडी हो जाती है। बस स्टॉप की तरफ़ देखो, तो वो ख़ाली है।"),
], next_=None)

scenes["r3_tapri"] = scene(
    "Kaka Ki Tapri", checkpoint="Kaka ki tapri", bg="cafe", time="night", weather="rain", music="night_city", cast=[(K, 0.7), (N, 0.36)],
    ai="Bansi Kaka's tea stall, rain drumming on the tin roof, ginger tea boiling. Naina, safe for now, opens up about Rohan: an ex-colleague who has followed her for months after she refused a coffee. Kaka is protective and funny.",
    steps=[
        nar("बस स्टॉप के पास बंसी काका की टपरी। टीन की छत पर बारिश, और उबलती चाय की ख़ुशबू।"),
        enter(K, 0.7), enter(N, 0.36, "left"),
        say(K, "अरे, इतनी बारिश में? आओ बेटा, अंदर आओ। गरमा-गरम चाय, अदरक वाली। पैसे बाद में।", "smile", "wave"),
        if_("confronted_rohan == true",
            [say(K, "वो हुडी वाला भाग गया? रोज़ यहाँ घूमता था। शाबाश, किसी ने तो टोका।", "happy", "laugh")],
            [say(K, "वो लड़का अभी भी कोने में खड़ा है। चिंता मत करो, बंसी की टपरी पर परिंदा भी पर नहीं मारता।", "determined", "cross_arms")]),
        say(N, "रोहन मेरे पुराने ऑफ़िस में था। मैंने एक बार कॉफ़ी के लिए मना किया। तब से पीछा नहीं छोड़ता।", "sad", "look_down"),
        say(N, "मैंने नौकरी बदली, रास्ता बदला। फिर भी।", "crying"),
        reply(N, "Naina se kuch kaho", ["Tumhari galti nahi hai", "Police complaint karte hain", "Kaka, chai mein extra adrak!"], [
            intent("support", "kind", 8, ["galti nahi", "tumhari galti", "strong", "himmat", "akeli nahi", "saath", "main hoon"], {"trust": 3, "affection": 2},
                   [say(N, "ये बात मुझे किसी से सुननी थी। छह महीने से।", "crying"), say(K, "सही कहा बेटा। ग़लती उसकी है।", "determined", "nod")]),
            intent("police", "confident", 7, ["police", "complaint", "fir", "report", "thana", "100", "112"], {"trust": 2, "courage": 1, "police_plan": True},
                   [say(N, "मैंने सोचा था। पर अकेले थाने जाने की हिम्मत नहीं हुई।", "worried"), say(K, "थाने का हवलदार मेरा पुराना ग्राहक है, बेटा।", "smug")]),
            intent("lighten", "funny", 5, ["chai", "adrak", "kaka", "biscuit", "maska", "pakoda", "garam"], {"affection": 2},
                   [say(K, "हाहा! अदरक इतना डालूँगा कि सारा डर भाग जाएगा।", "laugh", "laugh"), say(N, "आप दोनों पागल हैं।", "laugh")]),
        ], "आप कुछ कहेंगे नहीं?", "sad"),
        nar("तभी टपरी के बाहर, बारिश में, एक जानी-पहचानी हुडी।"),
        enter(R, 0.9),
        say(R, "नैना। बस पाँच मिनट। सिर्फ़ बात करनी है।", "determined"),
        emote(N, "scared"),
    ],
    prompt="Rohan tapri ke bilkul paas aa gaya hai.",
    choices=[
        ch("Naina ke saamne khade ho jao: 'Usne mana kiya. Baat khatam.' 🛡️", "confident", 12, {"courage": 3, "trust": 3, "confronted_rohan": True}, "r3b_after",
           [say(R, "तुम, फिर से तुम?", "angry"), say(K, "सुना नहीं? निकल। वरना हवलदार साहब को फ़ोन लगाऊँ?", "angry", "point"), say(R, "जा रहा हूँ।", "scared", "walk_away"), exit_(R)]),
        ch("Naina ko faisla karne do. Uski awaaz, uska haq.", "kind", 10, {"trust": 4, "affection": 1, "naina_spoke": True}, "r3b_after",
           [say(N, "रोहन। मेरा जवाब छह महीने पहले भी 'ना' था। आज भी 'ना' है। और कल सुबह थाने में भी 'ना' ही होगा।", "determined"), say(R, "ठीक है।", "sad", "walk_away"), exit_(R)]),
        ch("Kaka, aap sambhaliye… (peeche ho jao)", "timid", 1, {"courage": -2}, "r3b_after",
           [say(K, "जा बेटा, घर जा। यहाँ तेरी कोई बात नहीं सुनी जाएगी।", "angry", "point"), exit_(R)]),
    ])

scenes["r3b_after"] = scene(
    "Chai Ka Doosra Cup", bg="cafe", time="night", weather="rain", music="romance", cast=[(K, 0.72), (N, 0.36)],
    ai="Rohan has left. The tension breaks. Naina, grateful and a little shy, invites the player for chai on her building's rooftop tomorrow at 7 PM. Kaka teases them both.",
    steps=[
        say(N, "पता है, आज पहली बार उसके सामने मेरे हाथ नहीं काँपे।", "smile"),
        say(N, "सुनिए। कल शाम सात बजे मेरी बिल्डिंग की छत पर आइए। मैं अपने हाथ की चाय पिलाऊँगी, काका से भी अच्छी।", "shy"),
        say(K, "हाहा! ये चुनौती है, बेटा। मैं भी आऊँ जज बनकर?", "laugh", "laugh"),
    ],
    prompt="Naina jawab ka intezaar kar rahi hai.",
    choices=[
        ch("Pakka. Kal 7 baje. Aur ye mera number, kuch bhi ho toh call karna 📱", "kind", 10, {"start_objective": "o2", "gave_number": True, "affection": 2, "trust": 2, "complete_objective": "o1"}, "r4_chat",
           [say(N, "सेव कर लिया। 'बारिश वाला फ़रिश्ता' नाम से।", "blushing")]),
        ch("Chai toh theek hai… par main kal busy hoon.", "cold", 3, {"decline_objective": "o2", "affection": -2, "complete_objective": "o1", "gave_number": True}, "r4_chat",
           [say(N, "ओह। कोई बात नहीं। नंबर तो दे दीजिए, शुक्रिया कहने के लिए।", "sad")]),
    ])

scenes["r4_chat"] = scene(
    "Agla Din, 6:12 PM", type_="chat", bg=None, time="dusk", music="calm_campus", with_=N, header="Naina 🌧️",
    ai="Next day, 6:12 PM. Naina texts the player. Updates on Rohan, then asks if they are coming for rooftop chai at 7.",
    steps=[
        nar("अगले दिन, शाम छह बजकर बारह मिनट।"),
        if_("confronted_rohan == true or naina_spoke == true",
            [say(N, "आज सुबह थाने गई थी। काका भी साथ आए, छाता लेकर, हीरो की तरह।", "happy", time="6:12 PM", delay=1500),
             say(N, "रोहन को वार्निंग मिल गई। और उसने मेरा नंबर डिलीट कर दिया।", "smile", time="6:12 PM", delay=1300)],
            [say(N, "आज ऑफ़िस से आते वक़्त फिर लगा कोई पीछे है। शायद वहम था।", "worried", time="6:12 PM", delay=1500)]),
        say(N, "तो? छत पर चाय का इंतज़ाम कर दूँ?", "smile", time="6:13 PM", delay=1400),
        reply(N, "Naina ko reply karo", ["Aa raha hoon! Adrak zyada", "Thoda late ho jaunga", "Aaj nahi ho payega"], [
            intent("coming", "kind", 8, ["aa raha", "aa rahi", "aaunga", "aaungi", "haan", "pakka", "on my way", "coming", "adrak"], {"affection": 3},
                   [say(N, "अदरक ज़्यादा, नोट कर लिया। सात बजे, टाइम पर!", "happy", time="6:14 PM")], goto="r5_roof"),
            intent("late", "neutral", 3, ["late", "der", "thoda", "baad mein", "8 baje", "traffic"], {"affection": -1, "came_late": True},
                   [say(N, "ठीक है। चाय दो बार गरम कर लूँगी।", "bored", time="6:14 PM")], goto="r5_roof"),
            intent("busy", "cold", 1, ["nahi ho", "busy", "kaam", "aaj nahi", "sorry", "phir kabhi"], {"affection": -2, "fail_objective": "o2"},
                   [say(N, "ओह। ठीक है। फिर कभी।", "sad", time="6:15 PM")], goto="r6_end"),
        ], "हैलो? सीन करके भाग गए क्या?", "bored"),
    ],
    prompt="Naina ka message pada hai…",
    choices=[
        ch("Aa {m:raha|f:rahi} hoon. Adrak zyada daalna ☕", "kind", 8, {"affection": 3}, "r5_roof"),
        ch("(Ignore karo)", "cold", 0, {"affection": -4, "fail_objective": "o2"}, "r6_end", kind="ignore"),
    ])

scenes["r5_roof"] = scene(
    "Chhat Par Chai", checkpoint="Chhat par chai", bg="rooftop", time="dusk", music="romance", cast=[(N, 0.6)],
    ai="7 PM on Naina's building rooftop, fairy lights, cool sea breeze, homemade ginger tea. Naina talks about Mumbai finally not feeling like a stranger city, and about the night before. A gentle, possibly romantic moment.",
    steps=[
        obj("o2", "complete"),
        nar("छत पर हल्की-हल्की रोशनी की लड़ियाँ। दूर समंदर की तरफ़ से ठंडी हवा।"),
        enter(N, 0.6, "left"),
        if_("came_late == true",
            [say(N, "देर से सही। चाय फिर से बना दी है। तीसरी बार।", "bored", "cross_arms")],
            [say(N, "टाइम पे! लीजिए, काका से अच्छी है या नहीं, सच बताइए।", "happy")]),
        say(N, "पता है, मुंबई आकर पहली बार लगा कि ये शहर इतना भी अजनबी नहीं है।", "shy"),
        say(N, "कल रात अगर कोई न रुकता, तो ख़ैर। कोई रुका। आप।", "blushing", "look_down"),
        reply(N, "Naina se dil ki baat kaho", ["Chai kamaal hai!", "Agli baarish bhi saath?", "Tum bahut bahadur ho"], [
            intent("chai", "funny", 4, ["chai", "kamaal", "kaka", "achhi", "best", "badhiya"], {"affection": 2, "trust": 1},
                   [say(N, "हाहा! ये राज़ हमारे बीच। काका को मत बताना।", "laugh", "laugh")]),
            intent("romance", "kind", 8, ["baarish", "saath", "phir", "milna", "agli baar", "chhatri", "date", "pasand"], {"affection": 4},
                   [say(N, "वादा? मौसम विभाग कहता है, अगले हफ़्ते फिर बारिश है।", "blushing")]),
            intent("brave", "kind", 7, ["bahadur", "brave", "himmat", "strong", "thane", "proud", "tum ne"], {"trust": 3, "affection": 2},
                   [say(N, "बहादुर? मैं तो काँप रही थी। पर हाँ, आज थोड़ी बहादुर लग रही हूँ।", "smile")]),
        ], "आप कुछ बोलेंगे, या चाय ही पीते रहेंगे?", "smile"),
    ],
    prompt="Hawa thandi hai, chai garam.",
    choices=[
        ch("Baarish phir hogi. Agli baar bhi chhatri main hi {m:launga|f:laungi} ☂️", "kind", 10, {"affection": 4}, "r6_end",
           [say(N, "और अगर मैं अपनी छतरी भूल जाऊँ, जानबूझकर?", "blushing", "look_down")]),
        ch("Main bas madad kar {m:raha tha|f:rahi thi}. Isme kuch khaas nahi.", "cold", 2, {"affection": -2}, "r6_end",
           [say(N, "ओह। अच्छा, हाँ, बिल्कुल।", "sad")]),
    ])

scenes["r6_end"] = scene("Raat Dhalti Hai", bg="street", time="night", music="night_city", steps=[
    nar("बारिश थम चुकी है। सड़क पर स्ट्रीटलाइट की परछाइयाँ, और एक रात जो याद रहेगी।"),
])

# ---------------- endings
scenes["e_romance"] = scene("Baarish Ke Baad", bg="rooftop", time="night", weather="rain", music="romance", cast=[(N, 0.6)], ending="romance", steps=[
    enter(N, 0.6),
    say(N, "अगले हफ़्ते फिर बारिश है। छतरी मत लाइएगा, मेरे पास एक ही काफ़ी है।", "blushing", "look_down"),
    nar("कुछ कहानियाँ बस स्टॉप पर शुरू होती हैं। ये उनमें से एक है।"),
])
scenes["e_hero"] = scene("Raat Ka Hero", bg="cafe", time="night", music="upbeat", cast=[(K, 0.62)], ending="hero", steps=[
    enter(K, 0.62),
    say(K, "पूरे मोहल्ले में बात फैल गई है। जिसने उस लड़के को टोका, उसकी चाय आज से फ़्री! ज़िंदगी भर!", "laugh", "laugh"),
    nar("उस रात किसी के लिए आवाज़ उठी। तुम्हारी आवाज़। यही काफ़ी है।"),
])
scenes["e_strangers"] = scene("Ajnabi Hi Sahi", bg="street", time="night", music="calm_campus", cast=[(N, 0.6)], ending="strangers", steps=[
    say(N, "शुक्रिया, अजनबी। आपका नंबर फ़ोन में रहेगा, और वो रात याद में।", "smile"),
    nar("दो लोग, एक बारिश, और फिर अपने-अपने रास्ते।"),
])
scenes["e_alone"] = scene("Akeli Raat", bg="street", time="night", weather="rain", music="sad_piano", ending="alone", steps=[
    nar("उस रात के बाद वो बस स्टॉप रोज़ रास्ते में आता है। और हर बारिश में वही सवाल: अगर उस रात रुकना चुना होता, तो?"),
])

game = {
    "id": "baarish-wali-raat", "title": "Baarish Wali Raat",
    "tagline": "Aakhri bus chhoot gayi. Bus stop par ek ladki, aur peeche ek hoodie.",
    "premise": "Mumbai ki ek bheegi raat. Aakhri bus chhoot chuki hai, aur bus stop par khadi Naina baar-baar peeche mudkar dekh rahi hai — hoodie wala ek ladka station se uske peeche hai. Ruk kar madad karoge, ladke se seedha baat karoge, ya apne raste chale jaoge? Bansi Kaka ki tapri, ek chhatri aur agle din ki chhat wali chai — sab tumhare jawab par tika hai.",
    "genre": "Romance Thriller", "tags": ["mumbai", "baarish", "romance", "hinglish"], "minutes": 12,
    "creator": "Kissa Studio", "cover": "cover.webp", "accent": "#5CC8D7", "language": "hi", "narrator_voice": "narrator_female_hindi",
    "player": {"role": "stranger", "role_text": "Raat ko ghar laut raha ek ajnabi", "rig": "player_choice", "name": "player_entered", "default_name": "Aarav", "goal": "Is raat ko sahi faisla lo"},
    "stats": {
        "affection": {"start": 0, "min": -10, "max": 20, "label": "Naina Affection", "icon": "favorite"},
        "trust": {"start": 0, "min": -10, "max": 20, "label": "Naina Trust", "icon": "handshake"},
        "courage": {"start": 2, "min": 0, "max": 20, "label": "Himmat", "icon": "bolt"},
    },
    "flags": {"shared_umbrella": False, "confronted_rohan": False, "gave_number": False, "came_late": False, "went_back": False, "police_plan": False, "naina_spoke": False},
    "items": {},
    "rank": {"score": 0, "levels": [{"name": "Ajnabi", "at": 0}, {"name": "Hamdard", "at": 25}, {"name": "Baarish Ka Hero", "at": 50}]},
    "live": {"tone_effects": {"kind": {"trust": 1}, "funny": {"affection": 1}, "confident": {"courage": 1}, "rude": {"trust": -1, "affection": -1}, "timid": {"courage": -1}}},
    "characters": characters,
    "objectives": [
        {"id": "o1", "text": "Naina ko surakshit ghar tak pahunchao", "given_by": "naina", "place": "Bandra West", "deadline": "Aaj raat", "accept_choice": True, "on_complete": {"rank": 8, "trust": 2}},
        {"id": "o2", "text": "Kal shaam 7 baje Naina ki chhat par chai", "given_by": "naina", "place": "Naina ki building ki chhat", "deadline": "Kal shaam 7 baje", "accept_choice": True, "on_complete": {"rank": 10, "affection": 2}, "on_fail": {"affection": -2}},
    ],
    "start": "r1",
    "scenes": scenes,
    "endings": [
        {"id": "romance", "title": "Baarish Ke Baad", "if": "affection >= 9 and trust >= 5", "scene": "e_romance", "teaser": "Kuch kahaniyan bus stop par shuru hoti hain.", "kind": "good"},
        {"id": "hero", "title": "Raat Ka Hero", "if": "courage >= 7 and confronted_rohan == true", "scene": "e_hero", "teaser": "Kisi ke liye khade hona.", "kind": "hero"},
        {"id": "strangers", "title": "Ajnabi Hi Sahi", "if": "trust >= 2", "scene": "e_strangers", "teaser": "Do log, ek baarish.", "kind": "neutral"},
        {"id": "alone", "title": "Akeli Raat", "if": "true", "scene": "e_alone", "teaser": "Agar rukna chuna hota toh?", "kind": "bad"},
    ],
}

if __name__ == "__main__":
    write(game)
