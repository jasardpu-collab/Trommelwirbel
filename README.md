# 🥁 Trommelwirbel

Die Wäsche-Erinnerung für Menschen mit ADHS (und alle anderen Vergesslichen).

1. **Waschmaschine anmachen** → in der App **„Waschmaschine läuft“** tippen
2. **Foto vom Display** machen (Schablone: Display ins Rechteck, Bullauge in den Kreis) – die App liest die Restzeit. Stimmt sie nicht, einfach drehen.
3. Wenn die Wäsche fertig ist, kommt ein **richtig nerviger Alarm** – laut, vibrierend, blinkend, auch bei gesperrtem Handy und geschlossener App.
4. **Ausschalten geht nur mit einem neuen Foto der Waschmaschine.** Du musst also hingehen. 😄

Wer keine Kamera nutzen möchte, nimmt **„Nur Wecker stellen“** – dann reicht 3 Sekunden gedrückt halten.

Zusätzlich stellt die App einen **Sicherheits-Wecker** in der normalen Uhr-App (abschaltbar in den Einstellungen).

## 📥 Installieren (Android)

👉 **[Trommelwirbel.apk herunterladen](https://github.com/jasardpu-collab/trommelwirbel/releases/latest/download/Trommelwirbel.apk)**

1. Link auf dem Handy öffnen, die Datei lädt herunter.
2. Datei öffnen. Beim ersten Mal fragt Android, ob der Browser Apps installieren darf → **erlauben**.
3. Falls „Play Protect“ warnt: **„Trotzdem installieren“** (die App ist nicht aus dem Play Store, deshalb kennt Google sie nicht).
4. App öffnen und die gelben Einrichtungs-Punkte antippen (Benachrichtigungen, Vollbild-Alarm, Akku-Optimierung).

Updates: einfach denselben Link nochmal öffnen und drüberinstallieren. Einstellungen bleiben erhalten.

## 🔒 Datenschutz

- Texterkennung und Bildprüfung laufen **komplett offline** auf dem Handy (Google ML Kit, Modelle in der App).
- **Kein Foto verlässt das Gerät, kein Foto wird gespeichert.** Gemerkt wird nur ein winziger 64-Bit-Fingerabdruck des Start-Fotos, um das Ausschalt-Foto zu vergleichen. Daraus lässt sich kein Bild zurückrechnen.
- Keine Internet-Berechtigung, keine Werbung, kein Tracking.

## Gut zu wissen

- Der Weckton kann nicht stumm sein. Er läuft über den Wecker-Kanal mit voller Lautstärke, auch im Lautlos-Modus.
- Klappt das Foto nach 5 Versuchen nicht, erscheint ein **Notausgang** (15 Sekunden gedrückt halten).
- Ganz unausschaltbar ist der Alarm nicht: Wer die App in den Android-Einstellungen zwangsbeendet, stoppt sie.

## Technik

Kotlin, CameraX, ML Kit (Text Recognition + Image Labeling, gebündelt). Wird bei jedem Push automatisch über GitHub Actions gebaut und als Release veröffentlicht.
Der Signaturschlüssel liegt bewusst im Repo, damit Updates immer drüberinstalliert werden können.
