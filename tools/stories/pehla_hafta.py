#!/usr/bin/env python3
"""Pehla Hafta (v4) — startup office drama. Run: python3 tools/stories/pehla_hafta.py"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from kit import *

V, Z = "vikram", "zoya"

characters = [
    {"id": V, "name": "Vikram Sir", "name_hi": "विक्रम सर", "rig": "male", "art": "vikram",
     "bio": "Team lead. Kabhi khud ka startup tha, doob gaya. Ab kisi ko maafi nahi deta.",
     "persona": "Vikram Rathore, 38, team lead at a Bengaluru startup. Former founder whose own startup failed; strict, dry, economical with words, tests people with pressure. Respects honesty, ownership and guts; despises excuses and credit-stealing. Calls interns by surname, rarely smiles, but secretly mentors people who don't give up. Speaks calm, low Hindi with corporate English words.",
     "look": {"skin": 2, "hair": "side_part", "hair_color": "ash", "face": "sharp", "eyes": "dark", "outfit": "formal_shirt", "outfit_color": "white", "accessories": ["watch", "beard"]},
     "voice": {"slot": "male_mature_hindi_1", "pitch": 0.9, "speed": 0.95}},
    {"id": Z, "name": "Zoya", "name_hi": "ज़ोया", "rig": "female", "art": "zoya",
     "bio": "Saath wali intern. Aligarh se, tez dimaag, tez zubaan. Dost ya rival?",
     "persona": "Zoya Khan, 22, fellow intern from Aligarh. Quick-witted, competitive, sarcastic, fast-talking, loves filter coffee and bad puns. Secretly wants to build her own startup for small-town women sellers. Has been underestimated all her life, so she tests whether people share credit. Loyal once trust is earned; cold when betrayed.",
     "look": {"skin": 2, "hair": "ponytail", "hair_color": "burgundy", "face": "soft", "eyes": "hazel", "outfit": "denim_jacket", "outfit_color": "blue", "accessories": ["earrings"]},
     "voice": {"slot": "female_young_hindi_3", "pitch": 1.05, "speed": 1.05}},
]

scenes = {}

scenes["w1"] = scene(
    "Somvaar, 9:30 AM", checkpoint="Pehla din", bg="office", music="calm_campus", cast=[(V, 0.66), (Z, 0.36)],
    ai="Monday morning, first day of internship at a Bengaluru startup. Vikram, the strict team lead, dumps an impossible task on the new intern: finish the client pitch deck by 10 AM tomorrow. Zoya, the other intern, watches with a smirk.",
    steps=[
        nar("सोमवार, सुबह साढ़े नौ बजे। नया ऑफ़िस, नया लैपटॉप, और कॉफ़ी मशीन के पास अजीब सी ख़ामोशी।"),
        enter(V, 0.66),
        say(V, "नए इंटर्न? बढ़िया। मैं विक्रम। यहाँ गुड मॉर्निंग नहीं, अपडेट बोला जाता है।", "neutral", "cross_arms"),
        say(V, "कल सुबह दस बजे क्लाइंट पिच है। डेक आधा अधूरा है। पूरा करना है, कल दस बजे तक।", "determined", "point"),
        enter(Z, 0.34, "left"),
        say(Z, "पहले ही दिन? वाह। मैं ज़ोया। तुम्हारे लिए फ़्लावर्स भेज दूँ, या सीधे श्रद्धांजलि?", "smug", "hands_on_hips"),
        say(V, "ख़ान, तुम्हारा मज़ाक़ बाद में। तो? मंज़ूर है, या आज ही घर जाने का इरादा है?", "angry"),
    ],
    prompt="Vikram Sir jawab ka intezaar kar rahe hain.",
    choices=[
        ch("Done, sir. Kal 10 baje deck aapki table pe hoga 💼", "confident", 10, {"start_objective": "o1", "impression": 2, "himmat": 1}, "w2",
           [say(V, "अच्छा। कॉन्फ़िडेंस पसंद है। अब काम से साबित करो।", "smile", "nod")]),
        ch("Sir… main try {m:karunga|f:karungi}, par itna kam time…", "timid", 1, {"start_objective": "o1", "impression": -1}, "w2",
           [say(V, "ट्राई नहीं। करना है। यहाँ कोई हाथ नहीं पकड़ेगा।", "disgusted", "shake_head"), say(Z, "ओह। बेचारा।", "bored")]),
        ch("Pehle hafte mein itna kaam? Ye fair nahi hai, sir.", "rude", 3, {"decline_objective": "o1", "impression": -3, "himmat": 2}, "w2_zoya",
           [say(V, "फ़ेयर? ठीक है। ख़ान, डेक तुम्हारा। और तुम, देखते हैं किस काम के हो।", "angry", "walk_away"), exit_(V)]),
    ])

scenes["w2"] = scene(
    "Ek Offer", bg="office", time="dusk", music="calm_campus", cast=[(Z, 0.6)],
    ai="Evening, office half empty. Zoya rolls her chair over and offers to work together on the deck: she has the market data, the player has the deadline. Half work, half credit. She is testing whether the player is a team player.",
    steps=[
        nar("शाम हो चुकी है। आधा ऑफ़िस ख़ाली। ज़ोया अपनी कुर्सी घुमाकर तुम्हारी तरफ़ आती है।"),
        enter(Z, 0.6, "left"),
        say(Z, "पहला दिन, और सीधा विक्रम सर का टारगेट? बधाई हो। पिछले इंटर्न ने तीसरे दिन रिज़ाइन किया था।", "smug", "cross_arms"),
        say(Z, "सुनो। मेरे पास पिछले क्लाइंट का पूरा मार्केट डेटा है। मिलकर करें? आधा काम, आधा क्रेडिट।", "thinking"),
        reply(Z, "Zoya ko jawab do", ["Deal, saath mein karte hain", "Main akele kar lunga", "Pehle batao, tumhe kya milega?"], [
            intent("team", "kind", 6, ["deal", "saath", "milkar", "team", "haan", "chalo", "together", "partner", "theek"], {"team": 3},
                   [say(Z, "डील। पर याद रखना, आधा क्रेडिट मेरा। और फ़िल्टर कॉफ़ी भी तुम्हारी तरफ़ से।", "happy", "laugh")]),
            intent("solo", "cold", 3, ["akele", "khud", "nahi chahiye", "solo", "alone", "zaroorat nahi"], {"himmat": 2, "team": -2},
                   [say(Z, "ओके, लोन वुल्फ़। ऑल द बेस्ट। सुबह मिलते हैं, अगर ज़िंदा रहे।", "bored", "turn")]),
            intent("suspicious", "neutral", 4, ["kya milega", "kyun", "fayda", "matlab", "trust", "why", "catch"], {"team": 1, "himmat": 1},
                   [say(Z, "स्मार्ट सवाल। मुझे दिखाना है कि मैं सिर्फ़ डेटा एंट्री वाली लड़की नहीं हूँ। ख़ुश?", "determined")]),
        ], "हैलो? मैं डील ऑफ़र कर रही हूँ, शादी नहीं। हाँ या ना?", "bored"),
    ],
    next_="w3_chat")

scenes["w2_zoya"] = scene(
    "Ulta Pada", bg="office", time="dusk", music="tension", cast=[(Z, 0.6)],
    ai="Evening. Because the player refused, Vikram dumped the deck on Zoya. She is angry at the player for ruining her night but still offers to work together.",
    steps=[
        nar("डेक अब ज़ोया के पास है। और विक्रम सर की नज़र में तुम, शिकायत करने वाले इंटर्न।"),
        enter(Z, 0.6, "left"),
        say(Z, "थैंक्स। तुम्हारी 'फ़ेयरनेस' की वजह से मेरी रात ख़राब हो गई।", "angry", "cross_arms"),
        say(Z, "वैसे, अकेले नहीं होगा। और तुम्हें भी इस ऑफ़िस में टिकना है। मदद का मूड हो तो बताना।", "worried"),
    ],
    choices=[
        ch("Sorry. Chalo, saath mein karte hain. Raat lambi hai ☕", "kind", 6, {"team": 4, "start_objective": "o1"}, "w3_chat",
           [say(Z, "ठीक है। स्लाइड पाँच से शुरू करो। और कॉफ़ी, तुम्हारी तरफ़ से।", "smile", "nod")]),
        ch("Tumhara deck, tumhari problem 😏", "rude", 2, {"team": -4, "himmat": 1}, "w3_chat",
           [say(Z, "वाह। याद रखूँगी।", "furious", "walk_away"), exit_(Z)]),
    ])

scenes["w3_chat"] = scene(
    "11:48 PM", type_="chat", bg=None, time="night", music="night_city", with_=Z, header="Zoya (Intern)",
    ai="11:48 PM, the player is still in the office working on the deck. Zoya texts from home: she has the last three slides ready with data and graphs and offers to send them. Then asks the uncomfortable question: whose name goes on the deck tomorrow?",
    steps=[
        nar("रात के ग्यारह अड़तालीस। डेक की स्लाइड चौदह। ऑफ़िस में बस तुम और एसी की आवाज़। फ़ोन वाइब्रेट होता है।"),
        say(Z, "अभी तक ऑफ़िस में? सिक्योरिटी गार्ड से दोस्ती हो गई होगी अब तक।", "smug", time="11:48 PM", delay=1500),
        say(Z, "मेरे पास आख़िरी तीन स्लाइड्स तैयार हैं। मार्केट डेटा, ग्राफ़, सब कुछ। भेज दूँ?", "thinking", time="11:49 PM", delay=1600),
        reply(Z, "Zoya ko reply karo", ["Haan bhejo, thank you!", "Nahi, main khud kar lunga", "Badle mein kya chahiye?"], [
            intent("yes", "kind", 5, ["haan", "bhejo", "send", "please", "thank", "zaroor", "yes", "bhej do"], {"team": 2, "used_zoya_slides": True},
                   [say(Z, "भेज दीं। स्लाइड सत्रह का ग्राफ़ मेरा फ़ेवरेट है। तारीफ़ करना मत भूलना।", "happy", time="11:50 PM")]),
            intent("no", "cold", 3, ["nahi", "khud", "zaroorat nahi", "no", "main kar", "rehne do"], {"himmat": 2, "team": -1},
                   [say(Z, "ओके। ईगो का भी अपना डेटा होता है। गुड लक।", "bored", time="11:50 PM")]),
            intent("price", "funny", 4, ["badle", "kya chahiye", "price", "coffee", "chai", "treat", "free"], {"team": 1, "used_zoya_slides": True},
                   [say(Z, "एक हफ़्ते की फ़िल्टर कॉफ़ी। डील? भेज रही हूँ।", "laugh", time="11:50 PM")]),
        ], "हैलो? सो गए क्या? हाँ या ना बोलो।", "bored"),
        say(Z, "एक बात और। कल विक्रम सर पूछेंगे कि डेक किसने बनाया। तब क्या कहने का इरादा है?", "worried", time="11:52 PM", delay=1800),
    ],
    prompt="Zoya 'typing…' ke baad ruk gayi hai.",
    choices=[
        ch("Dono ka naam jayega. Team hain hum 🤝", "kind", 8, {"team": 4, "shared_credit": True}, "w4_crash",
           [say(Z, "पहली बार किसी ने ऐसा कहा। थैंक्स। गुड नाइट!", "happy", time="11:53 PM")]),
        ch("Kal dekhte hain 😴", "neutral", 2, {"team": -1}, "w4_crash",
           [say(Z, "हम्म। ठीक है।", "sad", time="11:53 PM")]),
        ch("(Seen karke chhod do)", "cold", 1, {"team": -3}, "w4_crash", kind="ignore"),
        ch("(Block karo — focus chahiye)", "rude", 2, {"team": -6, "himmat": 1}, "w4_crash", kind="block"),
    ])

scenes["w4_crash"] = scene(
    "2:17 AM", checkpoint="Raat ka crash", bg="office", time="night", music="tension", cast=[],
    ai="2:17 AM, alone in the office. The laptop freezes and the deck file gets corrupted. The pitch is in 8 hours. Panic.",
    steps=[
        nar("रात के दो बजकर सत्रह मिनट। लैपटॉप की स्क्रीन जम जाती है। फिर एक मैसेज: फ़ाइल करप्ट हो गई है।"),
        fx(flash=True),
        nar("पिच में आठ घंटे बाकी हैं। और डेक, आधा उड़ चुका है।"),
    ],
    prompt="Ab kya?",
    choices=[
        ch("Ghabrao mat. Backup se phir se banao — poori raat sahi 💪", "confident", 8, {"himmat": 3, "allnighter": True}, "w5_pitch",
           [nar("सुबह छह बजे तक, चौदह स्लाइड्स फिर से तैयार। आँखें लाल, पर डेक ज़िंदा।")]),
        ch("Zoya ko call karo. Usse better koi nahi samjhega", "kind", 6, {"team": 3, "zoya_saved_night": True}, "w4b_call",
           cond="blocked_zoya == false"),
        ch("Vikram Sir ko sach-sach mail kar do", "neutral", 5, {"impression": 1, "honest_mail": True}, "w5_pitch",
           [nar("सुबह पाँच बजे जवाब आता है: 'ठीक है। जो है, उसके साथ आओ। बहाना नहीं, हल लाओ।'")]),
    ])

scenes["w4b_call"] = scene(
    "Raat Ka Call", type_="chat", bg=None, time="night", music="night_city", with_=Z, header="Zoya (Intern)",
    ai="2:20 AM. The player calls/texts Zoya for help after the file crashed. Zoya is sleepy but comes through, with sarcasm.",
    steps=[
        say(Z, "दो बजे? किसी का ब्रेकअप हुआ है या डेक का?", "sleepy", time="2:21 AM", delay=1400),
        say(Z, "करप्ट? ओके, घबराओ मत। मेरे पास कल शाम का वर्ज़न है। बीस मिनट दो।", "determined", time="2:22 AM", delay=1500),
        say(Z, "और सुनो, ये रात मैं कभी नहीं भूलूँगी। तुम भी मत भूलना।", "smile", time="2:40 AM", delay=1800),
        setfx(used_zoya_slides=True),
    ],
    choices=[
        ch("Tum legend ho, Zoya. Coffee ek mahine ki 🙏", "kind", 6, {"team": 3}, "w5_pitch",
           [say(Z, "एक महीना? लिख के दो।", "laugh", time="2:41 AM")]),
        ch("Thanks. Ab so jao, baaki main dekh {m:lunga|f:lungi}.", "neutral", 3, {"team": 1, "himmat": 1}, "w5_pitch"),
    ])

scenes["w5_pitch"] = scene(
    "Pitch", checkpoint="Pitch meeting", type_="pov", bg="office", music="tension", cast=[(V, 0.5)],
    ai="10 AM, meeting room. Vikram and the client team are watching. The client's head just asked a tough question: why should they trust a small startup with their launch? The player must answer on the spot.",
    steps=[
        obj("o2"),
        nar("सुबह दस बजे। मीटिंग रूम। सामने विक्रम सर, पीछे क्लाइंट की टीम। सबकी नज़र तुम पर।"),
        say(V, "तो। डेक तुम्हारा है। शुरू करो।", "neutral", "cross_arms"),
        nar("बीस मिनट, चौदह स्लाइड्स। फिर क्लाइंट की हेड, मिसेज़ अय्यर, चश्मा उतारती हैं।"),
        nar("सवाल सीधा है: इतने छोटे स्टार्टअप पर हम अपना लॉन्च क्यों भरोसा करें?"),
        reply(V, "Client ko jawab do — apne shabdon mein", ["Data dekhiye, numbers jhooth nahi bolte", "Hum chhote hain, isliye fast hain", "Sach kahun toh guarantee nahi, par mehnat ki hai"], [
            intent("data", "confident", 10, ["data", "numbers", "graph", "market", "research", "slide", "growth", "percent"], {"impression": 3, "pitch_strong": True},
                   [say(V, "डेटा ने बात कर दी। अच्छा जवाब।", "smile", "nod")]),
            intent("fast", "confident", 8, ["chhote", "fast", "jaldi", "speed", "flexible", "agile", "turant"], {"impression": 2, "pitch_strong": True},
                   [say(V, "छोटा होना कमज़ोरी नहीं, रफ़्तार है। हम्म। मेरी लाइन चुरा ली।", "smug")]),
            intent("honest", "kind", 7, ["sach", "guarantee", "mehnat", "honest", "imaandari", "koshish", "trust"], {"impression": 2, "himmat": 1},
                   [say(V, "ईमानदार जवाब। क्लाइंट्स को आजकल यही चाहिए।", "thinking")]),
            intent("bluff", "funny", 3, ["best", "number one", "google", "sabse", "duniya", "famous", "promise"], {"impression": -1},
                   [say(V, "बड़े दावे। अगली बार उनके पीछे डेटा भी लाना।", "disgusted", "shake_head")]),
        ], "जवाब नहीं है? क्लाइंट इंतज़ार नहीं करते।", "angry", {"impression": -2}),
        if_("used_zoya_slides == true",
            [say(V, "और ये मार्केट डेटा, काफ़ी गहरा है। किसने निकाला?", "surprised")],
            [say(V, "प्रेज़ेंटेशन ठीक था। पर कुछ डेटा पुराना है। किसने बनाया ये डेक?", "thinking")]),
    ],
    prompt="Vikram Sir ka sawaal: kiska kaam hai ye?",
    choices=[
        ch("Zoya ka bhi, sir. Ye team ka kaam hai.", "kind", 10, {"team": 3, "impression": 3, "shared_credit": True, "complete_objective": "o1"}, "w6_rooftop",
           [say(V, "ईमानदारी। ये सिखाई नहीं जाती। अच्छा काम, दोनों का।", "smile", "nod")], cond="used_zoya_slides == true"),
        ch("Maine, sir. Poori raat jaag kar 😎", "confident", 6, {"impression": 3, "team": -6, "took_credit": True, "complete_objective": "o1"}, "w6_rooftop",
           [say(V, "हम्म। अच्छा। बहुत अच्छा।", "smile")], cond="used_zoya_slides == true"),
        ch("Maine, sir. Purana data kal tak update kar {m:dunga|f:dungi} — promise.", "confident", 8, {"impression": 3, "complete_objective": "o1"}, "w6_rooftop",
           [say(V, "ग़लती मानना और सुधार का प्लान देना। यही चाहिए।", "smile")], cond="used_zoya_slides == false"),
        ch("Pata nahi sir… sabne thoda thoda kiya…", "timid", 0, {"impression": -3, "fail_objective": "o1"}, "w6_rooftop",
           [say(V, "पता नहीं? अपने ही काम का?", "disgusted", "shake_head")]),
    ])

scenes["w6_rooftop"] = scene(
    "Chhat Par Chai", bg="rooftop", time="dusk", music="romance", cast=[(Z, 0.6)],
    ai="Friday evening on the office rooftop with chai, city lights coming on. Zoya talks about how the week went. If the player shared credit, she reveals her dream of starting her own company and might ask the player to be her co-founder; if the player stole credit, she is hurt and cold.",
    steps=[
        obj("o2", "complete"),
        nar("शुक्रवार की शाम। ऑफ़िस की छत। शहर की बत्तियाँ जलने लगी हैं, और हाथ में कटिंग चाय।"),
        enter(Z, 0.6, "left"),
        if_("took_credit == true",
            [say(Z, "मीटिंग में तो कमाल कर दिया। मेरी स्लाइड्स के साथ।", "angry", "cross_arms"),
             say(Z, "ख़ैर। सबक़ मिला। इस शहर में किसी पर भरोसा मत करो। ख़ासकर कॉफ़ी पिलाने वालों पर।", "sad")],
            [say(Z, "एक राज़ बताऊँ? मैं ये जॉब ज़्यादा दिन नहीं करने वाली।", "shy"),
             say(Z, "अलीगढ़ में अम्मी अचार बेचती हैं। पूरा मोहल्ला ख़रीदता है, पर ऑनलाइन कोई नहीं जानता। मुझे ऐसी औरतों के लिए एक ऐप बनाना है।", "happy"),
             if_("team >= 6",
                 [say(Z, "और मुझे एक पार्टनर चाहिए। कोई ऐसा जो क्रेडिट बाँटना जानता हो। सोचना ज़रा।", "determined")],
                 [say(Z, "पर वो कहानी फिर कभी। अभी तो बस चाय।", "smile")])]),
        reply(Z, "Zoya se kuch kaho", ["Tumhara idea kamaal hai", "Sorry, Zoya", "Chai achhi hai"], [
            intent("idea", "kind", 6, ["idea", "app", "kamaal", "achaar", "ammi", "badhiya", "awesome", "amazing", "karo"], {"team": 2},
                   [say(Z, "सच में? किसी ने पहली बार हँसा नहीं।", "blushing")]),
            intent("sorry", "kind", 5, ["sorry", "maaf", "galti", "mistake", "nahi chahiye tha"], {"team": 3},
                   [say(Z, "सॉरी बोलना आता है? चलो, आधे नंबर वापस।", "smile")]),
            intent("chai", "funny", 3, ["chai", "cutting", "coffee", "mausam", "view", "shehar"], {"team": 1},
                   [say(Z, "चाय? मैं सपने बता रही हूँ और तुम्हें चाय की पड़ी है। ठीक है, अच्छी है।", "laugh")]),
        ], "हम्म? शहर देखने में खो गए?", "smile"),
    ],
    prompt="Zoya tumhari taraf dekh rahi hai.",
    choices=[
        ch("Partner? Chalo, karte hain! 🚀", "confident", 12, {"cofounder": True, "team": 2}, "w7_review",
           [say(Z, "सच में?! ठीक है, कल से प्लानिंग। नाम मैं सोचूँगी!", "laugh", "laugh")], cond="team >= 6 and took_credit == false"),
        ch("Abhi toh yahi job meri duniya hai. Pehle offer, phir sapne.", "neutral", 4, {"impression": 1}, "w7_review",
           [say(Z, "प्रैक्टिकल। विक्रम सर तुम्हें पसंद करेंगे।", "smile")]),
        ch("(Chupchaap shehar ko dekho)", "neutral", 1, {"himmat": -1}, "w7_review", kind="action"),
    ])

scenes["w7_review"] = scene(
    "Review", bg="office", time="night", music="calm_campus", cast=[(V, 0.62)],
    ai="Friday night, Vikram calls the intern into his cabin for the internship review. He tells a short story about his own failed startup and asks what the player learned this week.",
    steps=[
        nar("हफ़्ते का आख़िरी ईमेल: 'मेरे केबिन में आओ। विक्रम।'"),
        enter(V, 0.62),
        say(V, "बैठो। मेरा भी एक स्टार्टअप था। तीन साल, बारह लोग, और फिर, कुछ नहीं।", "thinking"),
        say(V, "पता है क्यों डूबा? मैं सब क्रेडिट ख़ुद लेता था। टीम एक-एक करके चली गई।", "sad", "look_down"),
        say(V, "तो बताओ। इस हफ़्ते क्या सीखा?", "neutral"),
        reply(V, "Vikram Sir ko jawab do", ["Team ke bina kuch nahi", "Pressure mein bhi kaam karna", "Abhi bahut kuch seekhna hai"], [
            intent("team", "kind", 8, ["team", "saath", "milkar", "zoya", "credit", "dost", "together"], {"impression": 2, "team": 1},
                   [say(V, "हम्म। ये सबक़ मैंने तीन साल में सीखा। तुमने पाँच दिन में।", "smile")]),
            intent("pressure", "confident", 7, ["pressure", "deadline", "raat", "himmat", "haar nahi", "kaam", "mehnat"], {"impression": 2, "himmat": 1},
                   [say(V, "प्रेशर में टूटे नहीं। अच्छा है। पर अकेले मत झेलना।", "smile", "nod")]),
            intent("humble", "neutral", 5, ["seekhna", "abhi", "bahut", "pata nahi", "galti", "learn"], {"impression": 1},
                   [say(V, "जो ये मानता है, वो सीखता रहता है।", "smile")]),
        ], "सोचकर जवाब दो। मुझे रटे हुए जवाब पसंद नहीं।", "bored"),
        say(V, "ठीक है। मेरा फ़ैसला सोमवार को ईमेल में मिलेगा।", "neutral", "nod"),
    ],
    next_=None)

# ---------------- endings
scenes["e_startup"] = scene("Apna Startup", bg="cafe", time="night", music="upbeat", cast=[(Z, 0.6)], ending="startup", steps=[
    nar("तीन महीने बाद। एक छोटा सा कैफ़े, दो लैपटॉप, और स्क्रीन पर पहला ऑर्डर: अलीगढ़ से, आम का अचार।"),
    enter(Z, 0.6),
    say(Z, "पहला क्लाइंट! और विक्रम सर ने मेल भेजा: 'वो इंटर्न जिसने क्रेडिट बाँटा था, उसकी कंपनी में मेरा पहला निवेश।'", "laugh", "laugh"),
    nar("पहला हफ़्ता ख़त्म हुआ। और एक नई कहानी शुरू।"),
])
scenes["e_offer"] = scene("Full-Time Offer", bg="office", music="upbeat", cast=[(V, 0.66), (Z, 0.36)], ending="offer", steps=[
    say(V, "इस हफ़्ते दो लोगों ने मुझे सरप्राइज़ किया। दोनों को फ़ुल-टाइम ऑफ़र।", "smile", "nod"),
    say(Z, "टीम हैं हम, सर! और कॉफ़ी अभी भी इसकी तरफ़ से।", "laugh", "laugh"),
    nar("ऑफ़र लेटर पर तुम्हारा नाम। और साथ में एक दोस्त।"),
])
scenes["e_solo"] = scene("Akela Star", bg="office", time="night", music="sad_piano", cast=[(V, 0.62)], ending="solo", steps=[
    say(V, "ऑफ़र तुम्हारा है। काम अच्छा है। बस, टीम में कोई तुम्हारे साथ बैठना नहीं चाहता।", "neutral"),
    say(V, "मुझे भी कोई नहीं चाहता था। सोचना, किस रास्ते पर हो।", "sad", "look_down"),
    nar("ऑफ़र मिल गया। पर कैंटीन की टेबल पर तुम्हारे साथ वाली कुर्सी हमेशा ख़ाली रहती है।"),
])
scenes["e_intern"] = scene("Intern Hi Reh Gaye", bg="street", time="night", weather="rain", music="sad_piano", ending="intern", steps=[
    nar("ईमेल छोटा था: 'इस बार नहीं।' बारिश में बस स्टॉप पर पूरा हफ़्ता आँखों के सामने घूमता है।"),
    nar("अगली इंटर्नशिप में शायद, पहले दिन ही 'हाँ' कहना। और किसी की स्लाइड्स के लिए 'थैंक यू'।"),
])

# Zoya's block (chat) has to be visible to later gates
scenes["w3_chat"]["choice"][3]["effects"]["blocked_zoya"] = True

game = {
    "id": "pehla-hafta", "title": "Pehla Hafta",
    "tagline": "Ek raat, ek deck, ek pitch. Credit kiska hoga?",
    "premise": "Bengaluru ke ek startup mein tumhara pehla hafta. Team lead Vikram Rathore kisi ko maafi nahi deta, aur saath wali intern Zoya Khan dost hai ya rival — pata nahi. Kal subah 10 baje client pitch hai, deck aadha adhoora, aur raat 2 baje laptop crash. Credit kiska hoga, aur Friday ki review mein kya milega — faisla tumhara.",
    "genre": "Office Drama", "tags": ["startup", "office", "dosti", "hinglish"], "minutes": 14,
    "creator": "Kissa Studio", "cover": "cover.webp", "accent": "#4FA3FF", "language": "hi", "narrator_voice": "narrator_female_hindi",
    "player": {"role": "intern", "role_text": "Naya intern, pehla hafta", "rig": "player_choice", "name": "player_entered", "default_name": "Aarav", "goal": "Pehla hafta khatam hone tak full-time offer kamao"},
    "stats": {
        "impression": {"start": 0, "min": -10, "max": 20, "label": "Vikram Impression", "icon": "work"},
        "team": {"start": 0, "min": -10, "max": 20, "label": "Zoya Team", "icon": "group"},
        "himmat": {"start": 3, "min": 0, "max": 20, "label": "Himmat", "icon": "bolt"},
    },
    "flags": {"used_zoya_slides": False, "shared_credit": False, "took_credit": False, "cofounder": False, "blocked_zoya": False,
              "allnighter": False, "zoya_saved_night": False, "honest_mail": False, "pitch_strong": False},
    "items": {},
    "rank": {"score": 0, "levels": [{"name": "Intern", "at": 0}, {"name": "Pro", "at": 25}, {"name": "Rockstar", "at": 50}]},
    "live": {"tone_effects": {"kind": {"team": 1}, "confident": {"impression": 1}, "funny": {"team": 1}, "rude": {"team": -1, "impression": -1}, "timid": {"himmat": -1}}},
    "characters": characters,
    "objectives": [
        {"id": "o1", "text": "Client deck kal subah tak ready karo", "given_by": "vikram", "place": "Office", "deadline": "Kal subah 10 baje", "accept_choice": True, "on_complete": {"rank": 8, "impression": 1}, "on_fail": {"impression": -2}},
        {"id": "o2", "text": "Pitch meeting mein client ko convince karo", "given_by": "vikram", "place": "Meeting room", "deadline": "Kal 10 AM", "on_complete": {"rank": 10}},
    ],
    "start": "w1",
    "scenes": scenes,
    "endings": [
        {"id": "startup", "title": "Apna Startup", "if": "cofounder == true", "scene": "e_startup", "teaser": "Kabhi kabhi sabse achha offer boss nahi deta.", "kind": "secret"},
        {"id": "offer", "title": "Full-Time Offer", "if": "impression >= 5 and team >= 4", "scene": "e_offer", "teaser": "Kaam aur dosti, dono.", "kind": "good"},
        {"id": "solo", "title": "Akela Star", "if": "impression >= 5", "scene": "e_solo", "teaser": "Top par jagah kam hoti hai.", "kind": "bittersweet"},
        {"id": "intern", "title": "Intern Hi Reh Gaye", "if": "true", "scene": "e_intern", "teaser": "Har hafta pehla hafta nahi hota.", "kind": "bad"},
    ],
}

if __name__ == "__main__":
    write(game)
