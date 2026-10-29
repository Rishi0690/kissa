#!/usr/bin/env python3
"""Aukaat (v4) — college drama. Run: python3 tools/stories/aukaat.py"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from kit import *

T, S, M = "tanvi", "sahil", "meher"

characters = [
    {"id": T, "name": "Tanvi", "name_hi": "तन्वी", "rig": "female", "art": "tanvi",
     "bio": "Campus ki queen. Tez zubaan, mehngi coffee, aur ek raaz jo use raat ko sone nahi deta.",
     "persona": "Tanvi Oberoi, 20, the most popular girl in college, daughter of a rich builder. Sharp, sarcastic, fast one-liners, mixes English words into Hindi, never says sorry first. Proud on the outside; underneath she is scared because Sahil is blackmailing her with a video of her breaking down in front of her strict father. Warms up slowly only to people who are honest and don't pity her. Hates being called 'madam'.",
     "look": {"skin": 1, "hair": "long_wavy", "hair_color": "black", "face": "sharp", "eyes": "brown", "outfit": "college_blazer", "outfit_color": "maroon", "accessories": ["earrings"]},
     "voice": {"slot": "female_young_hindi_1", "pitch": 1.04, "speed": 1.03}},
    {"id": S, "name": "Sahil", "name_hi": "साहिल", "rig": "male", "art": "sahil",
     "bio": "Tanvi ka 'dost'. Muskurata hai, phir neeche girata hai.",
     "persona": "Sahil Malhotra, 21, rich, smooth-talking campus bully. Smirks, calls people 'chhotu' and 'hero', speaks slowly and confidently, makes veiled threats, never raises his voice unless cornered. Secretly insecure and greedy: he is blackmailing Tanvi for money. Backs down when someone calmly stands up to him in public.",
     "look": {"skin": 2, "hair": "undercut", "hair_color": "dark_brown", "face": "sharp", "eyes": "dark", "outfit": "denim_jacket", "outfit_color": "black", "accessories": ["watch"]},
     "voice": {"slot": "male_young_hindi_1", "pitch": 0.97, "speed": 1.0}},
    {"id": M, "name": "Meher", "name_hi": "मेहर", "rig": "female", "art": "meher",
     "bio": "Class ki topper. Kam bolti hai, sab dekhti hai. Purane gaano ki fan.",
     "persona": "Meher Siddiqui, 19, quiet class topper with round glasses. Observant, dry deadpan humour, kind but not a pushover, quotes old Hindi film songs, protective of new students because she was bullied last year. Speaks softly and precisely, a little shy when complimented.",
     "look": {"skin": 3, "hair": "braid", "hair_color": "dark_brown", "face": "round", "eyes": "dark", "outfit": "kurta", "outfit_color": "mustard", "accessories": ["glasses_round"]},
     "voice": {"slot": "female_young_hindi_2", "pitch": 1.06, "speed": 0.96}},
]

scenes = {}

scenes["s1"] = scene(
    "Pehla Din", checkpoint="Pehla din", bg="college_corridor", music="calm_campus", cast=[(T, 0.68), (S, 0.86)], audience=6,
    ai="First day of college. The player just bumped into Tanvi in a crowded corridor and spilled her coffee on her blazer. Everyone is watching. Tanvi is furious and wants the newcomer to look small; Sahil is enjoying it.",
    steps=[
        obj("o1"),
        nar("कॉलेज का पहला दिन। हाथ में किताबें, दिल में घबराहट, और सामने पूरा कॉरिडोर।"),
        enter(T, 0.6), enter(S, 0.82),
        {"prop": "coffee_cup", "char": T, "anchor": "hand_r"},
        nar("एक पल की टक्कर, और तन्वी की कॉफ़ी उसके ब्लेज़र पर।"),
        cam(1.15, T, 0.6),
        {"prop": "none", "char": T, "anchor": "hand_r"},
        say(T, "अंधे हो क्या? ये ब्लेज़र तुम्हारी पूरी फ़ीस से महँगा है!", "furious", "step_back"),
        crowd("gasp"),
        say(S, "अरे वाह। पहला दिन, और सीधा क्वीन से टक्कर? हिम्मत तो है छोटू में।", "smug", "cross_arms"),
        crowd("laugh"),
        say(T, "नाम क्या है तुम्हारा? ज़ोर से बोलो, पूरा कॉरिडोर सुन रहा है।", "angry", "point"),
        reply(T, "Tanvi ko jawab do — apne shabdon mein", ["Mera naam yaad rakhna", "Sorry, galti ho gayi", "Dhakka tumne diya tha"], [
            intent("name", "confident", 6, ["naam", "mera naam", "yaad rakhna", "main hoon", "hoon", "call me", "my name"], {"power": 2},
                   [say(T, "हम्म। नाम तो याद रहेगा। देखते हैं कब तक।", "smug", "hands_on_hips")]),
            intent("sorry", "timid", 0, ["sorry", "maaf", "galti", "mistake", "please", "maafi"], {"power": -1},
                   [say(T, "सॉरी से दाग़ नहीं जाते, जानेमन।", "disgusted", "shake_head"), crowd("laugh")]),
            intent("attitude", "confident", 8, ["dhakka", "tumne", "dekh ke", "tameez", "khud", "your fault", "galti tumhari", "phone"], {"power": 3, "trust": 1},
                   [say(T, "ओहो। ज़ुबान भी है? दिलचस्प।", "surprised", "step_back"), crowd("gasp")]),
            intent("joke", "funny", 5, ["bill", "dry clean", "laundry", "madam", "coffee", "mehnga", "joke"], {"power": 2},
                   [say(T, "मज़ाक़? पहले दिन? बहादुर हो या बेवक़ूफ़, जल्दी पता चल जाएगा।", "smug")]),
        ], "कुछ बोलना भी है, या बस घूरना है?", "bored", {"power": -1}),
        say(S, "चल तन्वी। इसकी औक़ात नहीं है हमारे लेवल की।", "laugh", "laugh"),
    ],
    prompt="Sab dekh rahe hain. Tum kya karte ho?",
    choices=[
        ch("Naam yaad rakhna — {player}. Aur dhakka tumne diya tha 😎", "confident", 12, {"power": 3, "trust": 1, "stood_up": True}, "s2_bold",
           [emote(T, "shocked"), say(T, "ठीक है, हीरो। ये चेहरा याद रहेगा।", "smug", "turn"), crowd("gasp"), exit_(T)]),
        ch("Dry cleaning ka bill bhej dena, madam 😏", "funny", 6, {"power": 2}, "s2_bold",
           [say(T, "मैडम? सीरियसली? बहुत महँगा पड़ेगा ये मज़ाक़।", "angry", "walk_away"), crowd("laugh"), exit_(T)]),
        ch("S-sorry… galti ho gayi 😬", "timid", 0, {"power": -2}, "s2_dismissed",
           [say(T, "हाँ, ग़लती तो हुई है। अगली बार रास्ते से हटकर चलना।", "smug", "walk_away"), crowd("laugh"), exit_(T), exit_(S)]),
        ch("(Chup-chaap kitaabein uthao)", "timid", 1, {"dosti": 1, "power": -1}, "s2_dismissed",
           [say(S, "देखा? बोलती बंद। यही है नए लोगों की औक़ात।", "laugh", "point"), exit_(T), exit_(S)], kind="action"),
    ])

scenes["s2_dismissed"] = scene(
    "Bikhri Kitaabein", bg="college_corridor", music="sad_piano", cast=[(M, 0.64)], audience=3,
    ai="The crowd has left laughing. Meher, a quiet topper, kneels to help the player pick up their books. She is kind, observant and drops a hint that Tanvi fought with Sahil this morning.",
    steps=[
        nar("हँसी धीरे-धीरे दूर चली जाती है। ज़मीन पर बिखरी किताबें, और एक हाथ जो उन्हें उठाने झुकता है।"),
        enter(M, 0.62, "left"),
        say(M, "रुको, मैं उठा देती हूँ। फ़िज़िक्स की किताब पर कॉफ़ी, बेचारी।", "smile", "kneel"),
        say(M, "मैं मेहर। पिछले साल मेरे साथ भी यही हुआ था। फ़र्क़ बस इतना, कॉफ़ी की जगह समोसा था।", "smile"),
        say(M, "वैसे, आज सुबह तन्वी साहिल पर चिल्ला रही थी। रो भी रही थी शायद। कुछ तो गड़बड़ है।", "thinking", "look_down"),
    ],
    choices=[
        ch("Thanks Meher. Aaj ki pehli achhi insaan tum ho 🙂", "kind", 4, {"dosti": 3}, "s3_class",
           [say(M, "बस बस, ज़्यादा तारीफ़ से मैं शर्मा जाती हूँ। चलो, क्लास शुरू होने वाली है।", "blushing", "nod")]),
        ch("Samose wali kahani poori sunaao 😄", "funny", 3, {"dosti": 2, "power": 1}, "s3_class",
           [say(M, "वो कहानी तीन चाय के बाद सुनाई जाती है। चलो, पहले क्लास।", "laugh", "laugh")]),
        ch("Mujhe kisi ki help nahi chahiye.", "cold", 2, {"dosti": -2, "power": 1}, "s3_class",
           [say(M, "ठीक है। जैसी तुम्हारी मर्ज़ी।", "sad", "step_back"), exit_(M, "left")]),
    ])

scenes["s2_bold"] = scene(
    "Seedha Saamna", type_="pov", bg="college_corridor", music="tension", cast=[(S, 0.5)], audience=8,
    ai="Tanvi has walked off. Sahil steps right into the player's face in the corridor to intimidate the newcomer who talked back to Tanvi. A crowd is watching.",
    steps=[
        nar("तन्वी जा चुकी है। पर साहिल सीधा तुम्हारे सामने आ खड़ा होता है।"),
        cam(1.25, S),
        say(S, "बड़ा हीरो बनने का शौक़ है? सबके सामने तन्वी से ऐसे बात?", "angry", "point"),
        say(S, "यहाँ हर कोई अपनी औक़ात में रहता है। तुम्हारी औक़ात मैं तय करूँगा।", "smug", "hands_on_hips"),
        crowd("murmur"),
    ],
    prompt="Sahil tumhare chehre ke bilkul paas hai…",
    choices=[
        ch("Peeche hato, Sahil. Meri aukaat main khud tay {m:karta|f:karti} hoon 🔥", "confident", 10, {"power": 3, "stood_up": True}, "s3_class",
           [say(S, "चल, देख लेंगे।", "shocked", "step_back"), crowd("gasp")]),
        ch("Itni der se dekh {m:raha|f:rahi} hoon, tum Tanvi ke bodyguard ho kya? 😏", "funny", 7, {"power": 2}, "s3_class",
           [say(S, "बॉडीगार्ड नहीं, बॉस हूँ। जल्दी समझ जाओगे।", "smug"), crowd("laugh")]),
        ch("Main bas class {m:dhoondh raha tha|f:dhoondh rahi thi}…", "timid", 0, {"power": -2}, "s3_class",
           [say(S, "हाँ, वही ढूँढो। और रास्ते से हटकर।", "laugh", "laugh"), crowd("laugh")]),
    ])

scenes["s3_class"] = scene(
    "Group Project", bg="classroom", music="calm_campus", cast=[(M, 0.36), (T, 0.74)], audience=5,
    ai="Classroom. The professor has just put the player, Meher and Tanvi in the same project group. Tanvi is irritated. Her phone buzzes and she goes pale reading a message; Meher notices.",
    steps=[
        nar("दोपहर की क्लास। प्रोफ़ेसर ने ग्रुप बनाए, और क़िस्मत ने मज़ाक़ किया: तुम, मेहर, और तन्वी।"),
        if_("stood_up == true",
            [say(T, "वाह। कॉरिडोर वाला हीरो, अब मेरे ग्रुप में। आज का दिन बेहतरीन होता जा रहा है।", "bored", "cross_arms")],
            [say(T, "बढ़िया। अब कॉफ़ी गिराने वाले के साथ प्रोजेक्ट भी करना पड़ेगा।", "disgusted", "cross_arms")]),
        say(M, "प्रोजेक्ट का टॉपिक है, कैंपस में रैगिंग। काफ़ी, उम्मीद है, दिलचस्प रहेगा।", "smug"),
        {"prop": "phone", "char": T, "anchor": "hand_r"},
        nar("तभी तन्वी का फ़ोन काँपता है। स्क्रीन देखते ही उसका चेहरा सफ़ेद पड़ जाता है।"),
        emote(T, "scared"),
        say(T, "मुझे, मुझे जाना है। तुम दोनों कर लो, मेरा नाम डाल देना।", "worried", "turn"),
        exit_(T),
        say(M, "तुमने देखा? वो डरी हुई थी। तन्वी ओबेरॉय, डरी हुई।", "worried"),
    ],
    prompt="Meher tumhari taraf dekh rahi hai.",
    choices=[
        ch("Kuch toh gadbad hai. Pata lagate hain 🕵️", "confident", 5, {"dosti": 2, "curious": True}, "s4_canteen",
           [say(M, "जासूसी? मुझे पसंद है। लंच में कैंटीन चलो, साहिल वहीं बैठता है।", "smile", "nod")]),
        ch("Uska drama, uski problem. Hum project karte hain.", "cold", 2, {"dosti": 1, "power": 1}, "s4_canteen",
           [say(M, "हम्म। प्रैक्टिकल सोच। चलो, लंच के बाद लाइब्रेरी।", "thinking")]),
        ch("Sach mein uska naam daal dein? Mehnat hum karein? 😤", "rude", 3, {"power": 1, "trust": -1}, "s4_canteen",
           [say(M, "अगर नाम नहीं डाला, तो कल पूरा कॉलेज तुम्हारे पीछे होगा। सोच लो।", "worried")]),
    ])

scenes["s4_canteen"] = scene(
    "Canteen Ka Raja", bg="cafe", music="tension", cast=[(S, 0.7), (M, 0.32)], audience=7,
    ai="College canteen at lunch. Sahil sits like a king and tries to humiliate the player in public by ordering them to fetch his chai. The player overhears that Sahil is pressuring Tanvi for money over a video.",
    steps=[
        nar("लंच ब्रेक। कैंटीन का सबसे अच्छा टेबल साहिल का है। हमेशा।"),
        enter(S, 0.7),
        say(S, "अरे, कॉरिडोर वाले। इधर आओ। मेरी चाय ले आओ, कड़क, दो चीनी।", "smug", "point"),
        crowd("laugh"),
        if_("dosti >= 3", [say(M, "ये तुम्हारा नौकर नहीं है, साहिल।", "determined", "cross_arms")]),
        say(S, "चलो चलो, सब देख रहे हैं। या आज भी किताबें उठाओगे?", "laugh", "laugh"),
    ],
    prompt="Poori canteen chup hai.",
    choices=[
        ch("Chai chahiye? Counter wahan hai. Paon tumhare bhi hain 😎", "confident", 10, {"power": 3, "stood_up": True}, "s4b_phone",
           [say(S, "ठीक है। बहुत ठीक है।", "angry", "cross_arms"), crowd("gasp")]),
        ch("Le aata hoon… do cheeni, kadak.", "timid", 0, {"power": -3}, "s4b_phone",
           [say(S, "शाबाश। ऐसे ही सीखते हैं।", "smug"), crowd("laugh")], kind="action"),
        ch("(Chai le aao — aur uske jootey par gira do) ☕", "funny", 6, {"power": 2, "trust": 1}, "s4b_phone",
           [cam(1.1, S, 0.5), say(S, "ये, ये मेरे नए जूते हैं!", "furious", "step_back"), crowd("laugh")], kind="action"),
    ])

scenes["s4b_phone"] = scene(
    "Galat Waqt, Sahi Jagah", bg="cafe", music="tension", cast=[(S, 0.72)],
    ai="Sahil steps aside to take a phone call. The player is close enough to hear him threaten Tanvi: pay money or a video of her goes viral at the fest.",
    steps=[
        {"prop": "phone", "char": S, "anchor": "hand_r"},
        nar("साहिल का फ़ोन बजता है। वो मुड़ता है, पर आवाज़ धीमी नहीं करता।"),
        say(S, "पैसे फ़्राइडे तक, तन्वी। वरना फ़ेस्ट में बड़ी स्क्रीन पर तुम्हारा वीडियो चलेगा। पूरा कॉलेज देखेगा क्वीन रोती कैसे है।", "smug", "hold_phone"),
        say(S, "और हाँ, पापा को बताया तो वीडियो उन्हें भी मिल जाएगा।", "angry", "hold_phone"),
        setfx(knows_secret=True),
        nar("तुमने सब सुन लिया। और शायद, साहिल को अभी पता नहीं।"),
    ],
    prompt="Tum ye baat kiske saath share karoge?",
    choices=[
        ch("Meher ko batao. Do dimaag ek se behtar 🤝", "kind", 4, {"dosti": 3, "meher_ally": True}, "s5_chat",
           [enter(M, 0.35, "left"), say(M, "ब्लैकमेल? ये तो क्राइम है। मैं साथ हूँ, पर सबूत चाहिए।", "determined", "nod")]),
        ch("Kisi ko mat batao. Ye mera card hai 🃏", "cold", 3, {"power": 2}, "s5_chat"),
    ])

scenes["s5_chat"] = scene(
    "1:03 AM", type_="chat", bg=None, time="night", music="night_city", with_=T, header="Tanvi Oberoi",
    ai="1:03 AM. Tanvi, who humiliated the player this morning, unexpectedly texts them. She is scared and proud at the same time and asks for help without wanting to beg.",
    steps=[
        nar("रात के एक बजकर तीन मिनट। फ़ोन की स्क्रीन जलती है। अनजान नंबर।"),
        say(T, "जाग रहे हो?", "worried", time="1:03 AM", delay=1800),
        say(T, "मैं तन्वी। हाँ, वही। नंबर ग्रुप लिस्ट से लिया।", "shy", time="1:03 AM", delay=1400),
        say(T, "मुझे तुम्हारी मदद चाहिए। और ये बात मैं दोबारा नहीं कहूँगी।", "determined", time="1:04 AM", delay=1600),
        reply(T, "Tanvi ko reply karo", ["Bolo, kya hua?", "Subah ki baat bhool gayi?", "Mujhse kyun?"], [
            intent("listen", "kind", 4, ["bolo", "kya hua", "batao", "sun", "theek ho", "okay", "haan", "sab theek"], {"trust": 2},
                   [say(T, "थैंक्स। ठीक नहीं हूँ। बिल्कुल नहीं।", "sad", time="1:05 AM")]),
            intent("taunt", "rude", 2, ["subah", "bhool", "bezzati", "insult", "ab yaad", "madad chahiye", "aukaat"], {"trust": -1, "power": 1},
                   [say(T, "हाँ, मैंने बदतमीज़ी की। सॉरी। अब ख़ुश?", "angry", time="1:05 AM")]),
            intent("why", "neutral", 3, ["kyun", "mujhse", "main hi", "why me", "kyu", "aur koi"], {"trust": 1},
                   [say(T, "क्योंकि तुम अकेले हो जो सबके सामने मुझसे डरे नहीं।", "shy", time="1:05 AM")]),
        ], "जवाब दो ना। मैं ज़्यादा देर हिम्मत नहीं रख पाऊँगी।", "worried"),
        say(T, "साहिल के पास मेरा एक वीडियो है। पापा के सामने मैं टूट गई थी, रो रही थी। वो उसे फ़ेस्ट में चलाएगा।", "crying", time="1:06 AM", delay=2200),
        if_("knows_secret == true", [me("Mujhe pata hai. Maine canteen mein uska call sun liya tha.", "1:06 AM"),
                                     say(T, "तुमने सुना? तो तुम्हें पता है वो कितना गिरा हुआ है।", "shocked", time="1:07 AM")]),
        say(T, "कल शाम पाँच बजे, लाइब्रेरी की सीढ़ियाँ। आओगे?", "worried", time="1:07 AM", delay=1500),
    ],
    prompt="Tanvi jawab ka intezaar kar rahi hai…",
    choices=[
        ch("Aaunga. Aur Sahil ko uski aukaat hum dikhayenge 💪", "confident", 10, {"trust": 4, "helped_tanvi": True, "start_objective": "o2"}, "s6_help",
           [say(T, "हम? ठीक है। हम।", "smile", time="1:08 AM")]),
        ch("Theek hai, aa jaunga. Par ehsaan nahi, insaaf ke liye.", "cold", 6, {"trust": 2, "helped_tanvi": True, "start_objective": "o2"}, "s6_help",
           [say(T, "मंज़ूर। मुझे ऐसे ही लोग चाहिए।", "determined", time="1:08 AM")]),
        ch("(Seen karke chhod do)", "cold", 1, {"trust": -3}, "s6_ignored", kind="ignore"),
        ch("(Block karo. Subah ka hisaab barabar 😈)", "rude", 4, {"trust": -6, "power": 2, "blocked_tanvi": True}, "s6_blocked", kind="block"),
    ])

scenes["s6_help"] = scene(
    "Library Ki Seedhiyan", checkpoint="Library ki seedhiyan", bg="library", time="dusk", music="romance", cast=[(T, 0.66)],
    ai="Library steps at sunset. Tanvi shows up without makeup or attitude. She is vulnerable but still proud. They need a plan to get proof of Sahil's blackmail before the fest on Friday.",
    steps=[
        obj("o2", "complete"),
        nar("शाम पाँच बजे। लाइब्रेरी की सीढ़ियाँ, ढलता सूरज। तन्वी बिना मेकअप, बिना ऐटिट्यूड।"),
        enter(T, 0.66),
        say(T, "आ गए। मुझे लगा था नहीं आओगे। मैं होती, तो नहीं आती।", "shy", "look_down"),
        say(T, "वो वीडियो, उस दिन पापा ने कहा था कि मैं उनकी सबसे बड़ी नाकामी हूँ। मैं बस रो पड़ी। साहिल ने रिकॉर्ड कर लिया।", "sad"),
        reply(T, "Tanvi se kuch kaho", ["Tum nakaami nahi ho", "Rona kamzori nahi hai", "Plan kya hai?"], [
            intent("comfort", "kind", 8, ["nakaami nahi", "kamzori nahi", "strong", "himmat", "theek ho jayega", "tum achhi", "galat nahi", "rona"], {"trust": 3},
                   [say(T, "ये, ये किसी ने पहली बार कहा है।", "blushing", "look_down")]),
            intent("plan", "confident", 6, ["plan", "kya karein", "sabut", "proof", "record", "police", "pakdenge"], {"power": 2, "trust": 1},
                   [say(T, "सीधे काम की बात। अच्छा है। मुझे भी रोना-धोना पसंद नहीं।", "determined")]),
            intent("tease", "funny", 4, ["queen", "makeup", "attitude", "coffee", "blazer", "madam"], {"trust": 1},
                   [say(T, "हँस लो, हँस लो। पर आज के लिए माफ़ किया।", "smile", "laugh")]),
        ], "कुछ तो बोलो। चुप्पी से डर लगता है।", "worried"),
        if_("meher_ally == true", [enter(M, 0.32, "left"),
            say(M, "माफ़ करना, बीच में आ गई। मेरे पास एक आइडिया है। साहिल से फ़ोन पर बात करवाओ, और मैं रिकॉर्ड करूँगी।", "determined", "nod"),
            say(T, "मेहर? तुम भी? पता नहीं क्यों, पर आज रोने का मन नहीं, हँसने का मन है।", "laugh")]),
        say(T, "फ़्राइडे तक पैसे चाहिए उसे। हमारे पास दो दिन हैं। बोलो, क्या करें?", "determined"),
    ],
    prompt="Plan chuno — Friday tak waqt hai.",
    choices=[
        ch("Sahil ko canteen bulao. Main chhupke record {m:karunga|f:karungi} 🎥", "confident", 10, {"power": 2, "trust": 2, "plan": "record", "start_objective": "o3"}, "s7_trap",
           [say(T, "रिस्की है। पर हाँ, यही रास्ता है।", "determined", "nod")]),
        ch("Principal ke paas chalte hain. Seedha, sach ke saath.", "kind", 6, {"trust": 1, "plan": "principal"}, "s7_principal",
           [say(T, "प्रिंसिपल, पापा के दोस्त हैं। पर ठीक है, कोशिश करते हैं।", "worried")]),
    ])

scenes["s6_ignored"] = scene(
    "Seen 1:09 AM", bg="college_corridor", music="sad_piano", cast=[(M, 0.6)],
    ai="Next morning. The player left Tanvi on seen. Tanvi avoids everyone. Meher is disappointed and tells the player that silence also takes a side.",
    steps=[
        nar("अगली सुबह। तन्वी कैंटीन नहीं आई। क्लास में भी नहीं।"),
        enter(M, 0.6, "left"),
        if_("dosti >= 3", [say(M, "सुनो। तन्वी ने कल रात तुम्हें मैसेज किया था ना? वो मेरी रूम में आकर रोई थी।", "sad")],
            [say(M, "पूरा हॉस्टल जानता है कि तन्वी कल रात रोई। और तुम्हें पता था, है ना?", "disgusted")]),
        say(M, "चुप रहना भी एक साइड चुनना होता है। फ़ेस्ट फ़्राइडे को है। सोच लो।", "determined", "cross_arms"),
    ],
    choices=[
        ch("Tum sahi ho. Chalo, Tanvi ko dhoondhte hain.", "kind", 6, {"dosti": 2, "trust": 1, "helped_tanvi": True}, "s7_trap",
           [say(M, "देर से सही, पर सही। चलो।", "smile", "nod")]),
        ch("Ye meri ladai nahi hai, Meher.", "cold", 1, {"dosti": -2}, "s8_fest",
           [say(M, "ठीक है। तो फ़्राइडे को बस तमाशा देखना।", "sad", "walk_away"), exit_(M, "left")]),
    ])

scenes["s6_blocked"] = scene(
    "Naya Dost?", bg="street", time="night", music="tension", cast=[(S, 0.64)],
    ai="Night outside college gate. Sahil somehow knows the player blocked Tanvi. He offers an alliance: help him humiliate Tanvi at the fest and the player becomes part of the 'top' group.",
    steps=[
        nar("कॉलेज गेट के बाहर, रात। साहिल अपनी बाइक पर, जैसे इंतज़ार कर रहा हो।"),
        enter(S, 0.64),
        say(S, "सुना है तुमने क्वीन को ब्लॉक कर दिया। मुझे तुम पसंद आने लगे हो।", "smug", "hands_on_hips"),
        say(S, "फ़्राइडे को फ़ेस्ट में एक छोटा सा शो है। तुम बस प्रोजेक्टर का बटन दबाना। बदले में, ये कैंपस तुम्हारा।", "smile", "point"),
    ],
    prompt="Sahil haath aage badhata hai.",
    choices=[
        ch("Deal. Usne mujhe sabke saamne zaleel kiya tha 😈", "rude", 8, {"revenge": True, "power": 4}, "s8_fest",
           [say(S, "समझदार। फ़्राइडे, सात बजे, स्टेज के पीछे।", "laugh", "laugh")]),
        ch("Main jitna bhi naraaz hoon, tumhare jaisa nahi hoon.", "confident", 8, {"power": 2, "trust": 2}, "s8_fest",
           [say(S, "आदर्शवादी। ऐसे लोग कैंटीन में चाय लाते हैं।", "angry", "walk_away"), exit_(S)]),
    ])

scenes["s7_principal"] = scene(
    "Principal Ka Office", bg="office", music="tension", cast=[(T, 0.64)],
    ai="Outside the principal's office. The principal refused to act without proof and said 'boys will be boys'. Tanvi is angry and hurt; they need proof now.",
    steps=[
        nar("दस मिनट बाद, प्रिंसिपल के ऑफ़िस के बाहर।"),
        enter(T, 0.64),
        say(T, "'सबूत लाओ, फिर बात करेंगे।' और साहिल के पापा का नाम सुनते ही चाय मँगवा ली।", "furious", "cross_arms"),
        say(T, "ठीक है। सबूत ही चाहिए ना? तो सबूत देंगे।", "determined"),
    ],
    choices=[
        ch("Toh plan B: canteen, Sahil, aur meri recording 🎥", "confident", 8, {"power": 2, "plan": "record", "start_objective": "o3"}, "s7_trap",
           [say(T, "अब आए ना असली बात पर।", "smile", "nod")]),
    ])

scenes["s7_trap"] = scene(
    "Jaal", checkpoint="Canteen ka jaal", type_="pov", bg="cafe", time="dusk", music="tension", cast=[(S, 0.5)], audience=2,
    ai="Evening, nearly empty canteen. Tanvi sits with Sahil as bait while the player secretly records from the next table. Sahil notices the player and walks over suspiciously, asking what they are doing.",
    steps=[
        nar("शाम की ख़ाली कैंटीन। तन्वी साहिल के सामने बैठी है। तुम बगल की टेबल पर, फ़ोन उल्टा रखे, रिकॉर्डिंग चालू।"),
        say(S, "पैसे लाई? गुड गर्ल। वरना फ़्राइडे को तुम्हारे आँसू, बड़ी स्क्रीन पर, फ़ुल एचडी में।", "smug", "cross_arms"),
        nar("रिकॉर्ड हो गया। तभी साहिल की नज़र तुम पर पड़ती है।"),
        cam(1.3, S, 0.4),
        say(S, "तुम? यहाँ क्या कर रहे हो? ये फ़ोन, इधर दो।", "angry", "point"),
        reply(S, "Sahil phone maang raha hai — kya kahoge?", ["Mera phone, meri marzi", "Bas game khel raha tha", "Ye recording principal tak jayegi"], [
            intent("firm", "confident", 10, ["mera phone", "marzi", "haath mat", "nahi dunga", "nahi dungi", "door raho", "peeche"], {"power": 3, "add_item": "proof_video"},
                   [say(S, "बहुत अकड़ है। ठीक है, जा रहा हूँ। पर ये ख़त्म नहीं हुआ।", "angry", "step_back")]),
            intent("bluff", "funny", 8, ["game", "khel", "ludo", "candy", "pubg", "video dekh", "reel"], {"power": 1, "add_item": "proof_video"},
                   [say(S, "गेम? हुह। बच्चे।", "bored", "turn")]),
            intent("threat", "confident", 6, ["principal", "police", "recording", "record", "sabut", "proof", "complaint"], {"power": 2, "add_item": "proof_video", "sahil_warned": True},
                   [say(S, "रिकॉर्डिंग? तुम, तुम्हें पता भी है मैं कौन हूँ?", "scared", "step_back")]),
        ], "बोलती बंद? फ़ोन दो, अभी!", "furious", {"power": -1}),
    ],
    prompt="Recording tumhare paas hai.",
    choices=[
        ch("Recording Tanvi ko bhejo. Uska sach, uska faisla 🤝", "kind", 8, {"trust": 4, "complete_objective": "o3"}, "s8_fest",
           [say(T, "तुमने, तुमने सच में कर दिखाया।", "crying")], cond="has(proof_video)"),
        ch("Recording apne paas rakho. Waqt aane par chalenge 🃏", "cold", 4, {"power": 3}, "s8_fest", cond="has(proof_video)"),
        ch("(Phone Sahil ko de do)", "timid", 0, {"power": -3, "trust": -3}, "s8_fest",
           [say(S, "डिलीट। देखा, कितना आसान था?", "laugh")], kind="action", cond="not has(proof_video)"),
        ch("Chalo yahan se, Tanvi. Ab nahi.", "neutral", 2, {"trust": 1}, "s8_fest", cond="not has(proof_video)"),
    ])

scenes["s8_fest"] = scene(
    "Fest Ki Raat", checkpoint="Fest ki raat", bg="college_corridor", time="night", music="upbeat", cast=[(S, 0.72), (T, 0.4)], audience=12,
    ai="Friday night, college fest, huge crowd, big screen. Sahil grabs the mic and announces a 'special video' about Tanvi. Everything depends on what the player does in the next ten seconds.",
    steps=[
        nar("फ़्राइडे की रात। फ़ेस्ट। रंगीन लाइटें, तेज़ म्यूज़िक, और स्टेज पर बड़ी स्क्रीन।"),
        enter(S, 0.72), enter(T, 0.4, "left"),
        {"prop": "mic", "char": S, "anchor": "hand_r"},
        say(S, "लेडीज़ एंड जेंटलमेन! आज एक स्पेशल वीडियो। हमारी क्वीन का असली चेहरा!", "laugh", "wave"),
        crowd("cheer"),
        emote(T, "scared"),
        if_("revenge == true",
            [say(S, "मेरे नए दोस्त, बटन तुम्हारे हाथ में है। दबाओ!", "smug", "point")],
            [say(T, "प्लीज़। कोई कुछ करो।", "crying", "look_down")]),
        cam(1.2, S, 0.3),
    ],
    prompt="Das second. Poora college dekh raha hai.",
    choices=[
        ch("Mic chheeno — aur Sahil ki recording chala do 🔊", "confident", 20, {"exposed_sahil": True, "power": 4, "trust": 3}, "s9_after",
           [{"prop": "none", "char": S, "anchor": "hand_r"}, nar("स्पीकर पर साहिल की आवाज़ गूँजती है: पैसे फ़्राइडे तक, वरना वीडियो।"), crowd("gasp"),
            say(S, "ये, ये झूठ है! ये एडिटेड है!", "scared", "step_back"), crowd("boo")], cond="has(proof_video) and revenge == false"),
        ch("(Projector ka plug kheench do) 🔌", "funny", 10, {"power": 2, "trust": 2, "saved_tanvi": True}, "s9_after",
           [fx(flash=True), nar("स्क्रीन काली। म्यूज़िक बंद। अँधेरे में सिर्फ़ साहिल की बौखलाई आवाज़।"), crowd("laugh"),
            say(S, "किसने किया ये? लाइट्स! कोई लाइट्स चालू करो!", "furious")], kind="action", cond="revenge == false"),
        ch("Button dabao. Hisaab barabar 😈", "rude", 8, {"power": 3, "trust": -8}, "s9_after",
           [nar("स्क्रीन पर तन्वी के आँसू। भीड़ में कुछ हँसते हैं, ज़्यादातर चुप।"), crowd("murmur"), say(T, "तुम भी।", "crying", "walk_away"), exit_(T, "left")], cond="revenge == true"),
        ch("Main ye nahi {m:karunga|f:karungi}, Sahil. Khel khatam.", "confident", 14, {"revenge": False, "power": 3, "trust": 3, "saved_tanvi": True}, "s9_after",
           [say(S, "तुम, तुम मुझे धोखा दे रहे हो?", "shocked", "step_back"), crowd("cheer")], cond="revenge == true"),
        ch("(Kuch mat karo. Bas dekho.)", "timid", 0, {"power": -2, "trust": -4}, "s9_after",
           [nar("वीडियो चलता है। तुम भीड़ का हिस्सा बने रहते हो।"), crowd("murmur")], kind="action"),
    ])

scenes["s9_after"] = scene(
    "Fest Ke Baad", bg="rooftop", time="night", music="romance", cast=[(M, 0.36)],
    ai="After the fest, on the college rooftop. Lights of the city below. Meher finds the player; they talk about what happened and what kind of person the player chose to be this week.",
    steps=[
        nar("फ़ेस्ट ख़त्म। कॉलेज की छत पर दूर तक शहर की बत्तियाँ।"),
        enter(M, 0.36, "left"),
        if_("exposed_sahil == true or saved_tanvi == true",
            [say(M, "तो, आज कॉलेज ने एक नया नाम सीखा। तुम्हारा।", "smile", "nod")],
            [say(M, "अजीब रात थी ना? कुछ लोग हीरो बने, कुछ भीड़।", "sad")]),
        say(M, "\"ये दुनिया अगर मिल भी जाए तो क्या है।\" पुराना गाना है। आज समझ आया।", "thinking"),
    ],
    prompt="Meher ke saath chhat par…",
    choices=[
        ch("Is hafte tum sabse sachchi dost nikli, Meher 💛", "kind", 6, {"dosti": 3}, None,
           [say(M, "बस, फिर से शर्मिंदा कर दिया। चाय पिलाओ, तो माफ़।", "blushing", "laugh")]),
        ch("Ab Tanvi se milna hai. Chalo, neeche chalte hain.", "neutral", 3, {"trust": 1}, None,
           [say(M, "जाओ। वो सीढ़ियों पर बैठी है, अकेली।", "smile")]),
    ])

# ---------------- endings
scenes["e_good"] = scene("Saath Khade", bg="library", time="dusk", music="romance", cast=[(T, 0.6)], ending="good", steps=[
    enter(T, 0.6),
    say(T, "साहिल सस्पेंड हो गया। और पापा, पापा ने पहली बार सॉरी कहा।", "happy"),
    say(T, "सुनो, कॉरिडोर वाले। कल कॉफ़ी मेरी तरफ़ से। और इस बार, तुम्हारे ऊपर नहीं गिराऊँगी।", "laugh", "laugh"),
    nar("पहले हफ़्ते में तुमने अपनी औक़ात ख़ुद लिखी। और एक क्वीन ने मुस्कुराना सीखा।"),
])
scenes["e_hero"] = scene("Sach Ki Awaaz", bg="college_corridor", music="upbeat", cast=[(M, 0.4)], audience=8, ending="hero", steps=[
    crowd("cheer"),
    say(M, "पूरे कैंपस में बस एक ही बात है। वो नया स्टूडेंट, जिसने साहिल को माइक पर नंगा कर दिया।", "laugh"),
    nar("तन्वी से रिश्ता शायद अभी भी अधूरा है। पर कॉरिडोर में अब लोग तुम्हें रास्ता देते हैं।"),
])
scenes["e_revenge"] = scene("Ab Power Tumhare Paas", bg="cafe", music="tension", cast=[(S, 0.7)], audience=6, ending="revenge", steps=[
    say(S, "देखा? अब कैंटीन की ये टेबल हमारी है। कुर्सी खींचो, बॉस।", "smug", "hands_on_hips"),
    nar("सबसे अच्छी टेबल, सबसे ऊँची हँसी। पर रात को, एक रोता हुआ चेहरा बार-बार याद आता है।"),
])
scenes["e_friend"] = scene("Meher Wala Rasta", bg="library", time="dusk", music="calm_campus", cast=[(M, 0.6)], ending="friend", steps=[
    say(M, "लाइब्रेरी की तीसरी रो, खिड़की वाली टेबल। अब से ये हमारी है। और अब, समोसे वाली कहानी।", "laugh", "laugh"),
    nar("हर कहानी में क्वीन ज़रूरी नहीं होती। कभी-कभी सबसे अच्छा दोस्त, किताबें उठाने वाला हाथ होता है।"),
])
scenes["e_lonely"] = scene("Akela Fresher", bg="college_corridor", time="dusk", music="sad_piano", ending="lonely", steps=[
    nar("पहला हफ़्ता ख़त्म। कॉरिडोर में कोई तुम्हारा नाम नहीं लेता। न अच्छे से, न बुरे से।"),
    nar("चुप रहना भी एक जवाब है। बस, वो कभी याद नहीं रहता।"),
])

game = {
    "id": "aukaat", "title": "Aukaat",
    "tagline": "Pehle din sabke saamne zaleel hue. Raat 1:03 baje usi ka message aaya.",
    "premise": "College ka pehla din. Campus ki queen Tanvi Oberoi ne sabke saamne tumhari bezzati kar di, aur uska dost Sahil tumhe 'aukaat' dikhane pe tula hai. Phir raat 1:03 baje Tanvi ka message aata hai: use tumhari madad chahiye. Madad, badla, ya chuppi? Har jawab tumhari power, trust aur dosti badalta hai — aur Friday ki fest raat sab tay karegi.",
    "genre": "College Drama", "tags": ["college", "revenge", "romance", "hinglish"], "minutes": 15,
    "creator": "Kissa Studio", "cover": "cover.webp", "accent": "#E8475F", "language": "hi", "narrator_voice": "narrator_hindi",
    "player": {"role": "new_student", "role_text": "Naya student jise sab kam samajhte hain", "rig": "player_choice", "name": "player_entered", "default_name": "Aarav", "goal": "Pehla hafta izzat ke saath survive karo"},
    "stats": {
        "power": {"start": 2, "min": 0, "max": 20, "label": "Power", "icon": "bolt"},
        "trust": {"start": 0, "min": -20, "max": 20, "label": "Tanvi Trust", "icon": "handshake"},
        "dosti": {"start": 0, "min": -10, "max": 20, "label": "Meher Dosti", "icon": "favorite"},
    },
    "flags": {"stood_up": False, "helped_tanvi": False, "revenge": False, "blocked_tanvi": False, "knows_secret": False, "meher_ally": False,
              "curious": False, "exposed_sahil": False, "saved_tanvi": False, "sahil_warned": False, "plan": "none"},
    "items": {"proof_video": "Sahil ki dhamki ki recording"},
    "rank": {"score": 0, "levels": [{"name": "Fresher", "at": 0}, {"name": "Bold", "at": 25}, {"name": "Campus Legend", "at": 55}]},
    "live": {"tone_effects": {"kind": {"trust": 1, "dosti": 1}, "confident": {"power": 1}, "funny": {"power": 1}, "rude": {"trust": -1, "dosti": -1}, "timid": {"power": -1}}},
    "characters": characters,
    "objectives": [
        {"id": "o1", "text": "Pehla din survive karo", "place": "College", "deadline": "Aaj", "on_complete": {"rank": 5}, "unlocks": "o2"},
        {"id": "o2", "text": "Tanvi se library ki seedhiyon par milo", "given_by": "tanvi", "place": "Library ki seedhiyan", "deadline": "Kal shaam 5 baje", "accept_choice": True, "on_complete": {"rank": 8, "power": 1}, "on_fail": {"power": -1}},
        {"id": "o3", "text": "Sahil ki dhamki record karo", "given_by": "tanvi", "place": "Canteen", "deadline": "Friday se pehle", "accept_choice": True, "on_complete": {"rank": 10}, "on_fail": {"trust": -2}},
    ],
    "start": "s1",
    "scenes": scenes,
    "endings": [
        {"id": "good", "title": "Saath Khade", "if": "(exposed_sahil == true or saved_tanvi == true) and trust >= 8", "scene": "e_good", "teaser": "Jab sach bolne ka waqt aaye…", "kind": "good"},
        {"id": "revenge", "title": "Ab Power Tumhare Paas", "if": "revenge == true", "scene": "e_revenge", "teaser": "Badla meetha hota hai. Hota hai na?", "kind": "revenge"},
        {"id": "hero", "title": "Sach Ki Awaaz", "if": "exposed_sahil == true or (saved_tanvi == true and power >= 8)", "scene": "e_hero", "teaser": "Mic ek, awaaz tumhari.", "kind": "hero"},
        {"id": "friend", "title": "Meher Wala Rasta", "if": "dosti >= 5", "scene": "e_friend", "teaser": "Har kahani mein queen zaroori nahi.", "kind": "secret"},
        {"id": "lonely", "title": "Akela Fresher", "if": "true", "scene": "e_lonely", "teaser": "Chup rehna bhi ek jawab hai.", "kind": "bad"},
    ],
}

# o1 completes when the first day ends (class scene entry)
scenes["s3_class"]["steps"].insert(0, obj("o1", "complete"))

if __name__ == "__main__":
    write(game)
