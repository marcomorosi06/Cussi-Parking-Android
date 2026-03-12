<?php
// Gestione lingua via cookie tecnico
$lang = isset($_COOKIE['lang']) ? $_COOKIE['lang'] : 'en';
if (!in_array($lang, ['en', 'it'])) {
    $lang = 'en';
}

$t = [
    'en' => [
        'title' => 'CUSSI PARKING // TERMINAL',
        'prompt' => 'root@cussiparking:~#',
        'cmd_start' => './init_system.sh',
        'status_ok' => '[ OK ] SYSTEM ONLINE. SELF-HOSTED ENVIRONMENT ACTIVE.',
        'headline' => 'YOUR SMART PARKING. PRIVATE, SECURE, YOURS.',
        'subhead' => 'NO THIRD-PARTY CLOUD. YOUR DATA REMAINS UNDER YOUR TOTAL CONTROL.',
        'cmd_features' => 'cat features.txt',
        'f1' => '[*] AUTOMATIC TRIGGERS: Disconnect from BT or tap NFC to save location.',
        'f2' => '[*] ABSOLUTE PRIVACY: You host the server. No tracking. 100% Open Source.',
        'f3' => '[*] FAMILY SHARING: Generate invite codes securely from the app.',
        'cmd_download' => 'wget --download android_app',
        'btn_github' => '[ DOWNLOAD ANDROID APP FROM GITHUB ]',
        'cookie_notice' => 'SYSTEM LOG: This terminal uses only technical cookies for local language preference.',
        'made_with' => 'EOF // Made by Marco Morosi',
        'switch_lang' => 'switch_lang --it',
        'switch_btn' => '[ IT ]'
    ],
    'it' => [
        'title' => 'CUSSI PARKING // TERMINALE',
        'prompt' => 'root@cussiparking:~#',
        'cmd_start' => './init_system.sh',
        'status_ok' => '[ OK ] SISTEMA ONLINE. AMBIENTE SELF-HOSTED ATTIVO.',
        'headline' => 'IL TUO PARCHEGGIO. PRIVATO, SICURO, TUO.',
        'subhead' => 'NESSUN CLOUD DI TERZE PARTI. I TUOI DATI RESTANO SOTTO IL TUO CONTROLLO.',
        'cmd_features' => 'cat features.txt',
        'f1' => '[*] TRIGGER AUTOMATICI: Spegni il BT o tocca un tag NFC per salvare la posizione.',
        'f2' => '[*] PRIVACY ASSOLUTA: Il server è tuo. Nessun tracciamento. 100% Open Source.',
        'f3' => '[*] CONDIVISIONE: Genera codici invito dall\'app per la famiglia.',
        'cmd_download' => 'wget --download android_app',
        'btn_github' => '[ SCARICA APP ANDROID DA GITHUB ]',
        'cookie_notice' => 'SYSTEM LOG: Questo terminale usa solo cookie tecnici per salvare la lingua.',
        'made_with' => 'EOF // Fatto da Marco Morosi',
        'switch_lang' => 'switch_lang --en',
        'switch_btn' => '[ EN ]'
    ]
];

$txt = $t[$lang];
?>
<!DOCTYPE html>
<html lang="<?php echo $lang; ?>">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title><?php echo $txt['title']; ?></title>
    <style>
        /* TEMA TERMINALE */
        :root {
            --bg: #050505;
            --term-green: #00ff41;
            --term-dark-green: #008f11;
            --term-font: 'Courier New', Courier, monospace;
        }

        body {
            background-color: var(--bg);
            color: var(--term-green);
            font-family: var(--term-font);
            margin: 0;
            padding: 20px;
            display: flex;
            flex-direction: column;
            min-height: 100vh;
            line-height: 1.5;
            overflow-x: hidden;
        }

        /* Scanline effect (sottile riga che scorre per fare effetto monitor CRT) */
        body::before {
            content: " ";
            display: block;
            position: absolute;
            top: 0; left: 0; bottom: 0; right: 0;
            background: linear-gradient(rgba(18, 16, 16, 0) 50%, rgba(0, 0, 0, 0.25) 50%), linear-gradient(90deg, rgba(255, 0, 0, 0.06), rgba(0, 255, 0, 0.02), rgba(0, 0, 255, 0.06));
            z-index: 2;
            background-size: 100% 2px, 3px 100%;
            pointer-events: none;
            opacity: 0.6;
        }

        .container {
            max-width: 800px;
            margin: 0 auto;
            width: 100%;
            z-index: 10;
            position: relative;
        }

        header {
            display: flex;
            justify-content: space-between;
            border-bottom: 1px dashed var(--term-dark-green);
            padding-bottom: 10px;
            margin-bottom: 20px;
        }

        .lang-switch {
            background: none;
            border: 1px solid var(--term-green);
            color: var(--term-green);
            font-family: var(--term-font);
            cursor: pointer;
            padding: 2px 8px;
            font-weight: bold;
            transition: all 0.2s;
        }

        .lang-switch:hover {
            background: var(--term-green);
            color: var(--bg);
        }

        .prompt { color: #008f11; font-weight: bold; }
        .cmd { color: #fff; }
        
        .line { margin-bottom: 10px; word-wrap: break-word; }
        .output { margin-left: 20px; margin-bottom: 20px; color: #00ff41; }
        .highlight { color: #fff; font-weight: bold; }

        /* LOGO PNG NORMALE */
        .logo-wrapper {
            display: flex;
            justify-content: center;
            margin: 30px 0;
        }

        .logo-img {
            max-width: 250px;
            height: auto;
        }

        /* ASCII BOX */
        .ascii-box {
            border: 1px solid var(--term-dark-green);
            padding: 15px;
            margin-bottom: 20px;
            background: rgba(0, 255, 65, 0.05);
        }

        /* BOTTONE GITHUB TERMINAL STYLE */
        .btn-download {
            display: inline-block;
            background: var(--bg);
            color: var(--term-green);
            border: 2px solid var(--term-green);
            padding: 10px 20px;
            text-decoration: none;
            font-weight: bold;
            margin-top: 10px;
            transition: all 0.2s;
        }

        .btn-download:hover {
            background: var(--term-green);
            color: var(--bg);
            box-shadow: 0 0 15px var(--term-green);
        }

        /* BLINKING CURSOR */
        .cursor {
            display: inline-block;
            width: 10px;
            height: 1.2em;
            background-color: var(--term-green);
            vertical-align: middle;
            animation: blink 1s step-end infinite;
        }

        @keyframes blink {
            0%, 100% { opacity: 1; }
            50% { opacity: 0; }
        }

        footer {
            margin-top: auto;
            padding-top: 40px;
            border-top: 1px dashed var(--term-dark-green);
            font-size: 0.85em;
            color: var(--term-dark-green);
        }

        @media (max-width: 600px) {
            body { font-size: 14px; }
            .logo-img { max-width: 200px; }
        }
    </style>
</head>
<body>

<div class="container">
    <header>
        <div>CUSSI_PARKING</div>
        <button class="lang-switch" onclick="toggleLang()">
            <?php echo $txt['switch_btn']; ?>
        </button>
    </header>

    <div class="line">
        <span class="prompt"><?php echo $txt['prompt']; ?></span> 
        <span class="cmd"><?php echo $txt['cmd_start']; ?></span>
    </div>

    <div class="logo-wrapper">
        <img src="logo.png" alt="Cussi Parking Logo" class="logo-img">
    </div>

    <div class="output">
        <?php echo $txt['status_ok']; ?><br><br>
        <span class="highlight"><?php echo $txt['headline']; ?></span><br>
        <?php echo $txt['subhead']; ?>
    </div>

    <div class="line">
        <span class="prompt"><?php echo $txt['prompt']; ?></span> 
        <span class="cmd"><?php echo $txt['cmd_features']; ?></span>
    </div>

    <div class="ascii-box">
        <?php echo $txt['f1']; ?><br><br>
        <?php echo $txt['f2']; ?><br><br>
        <?php echo $txt['f3']; ?>
    </div>

    <div class="line">
        <span class="prompt"><?php echo $txt['prompt']; ?></span> 
        <span class="cmd"><?php echo $txt['cmd_download']; ?></span>
    </div>

    <div class="output">
        <a href="https://github.com/marcomorosi06/Cussi-Parking-Android" class="btn-download" target="_blank">
            <?php echo $txt['btn_github']; ?>
        </a>
    </div>

    <div class="line">
        <span class="prompt"><?php echo $txt['prompt']; ?></span> 
        <span class="cursor"></span>
    </div>

    <footer>
        <div><?php echo $txt['cookie_notice']; ?></div>
        <div style="margin-top: 10px;"><?php echo $txt['made_with']; ?></div>
    </footer>
</div>

<script>
    // Gestione cookie tecnica senza librerie esterne
    function toggleLang() {
        const currentLang = "<?php echo $lang; ?>";
        const newLang = currentLang === 'en' ? 'it' : 'en';
        const d = new Date();
        d.setTime(d.getTime() + (365*24*60*60*1000));
        document.cookie = "lang=" + newLang + ";expires=" + d.toUTCString() + ";path=/;SameSite=Strict";
        window.location.reload();
    }
</script>

</body>
</html>
