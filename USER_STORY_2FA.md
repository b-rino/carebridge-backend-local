# User Story: Tvungen 2FA-opsætning og verificering

---

**Som** systembruger
**vil jeg** blive tvunget til at opsætte en 2FA-metode ved første login
**så** min konto er bedre beskyttet mod uautoriseret adgang.

---

## Acceptkriterier

**AC1 — Tvungen 2FA-opsætning ved første login**

- **Givet** at en bruger ikke har en aktiv 2FA-konfiguration
- **Når** brugeren indtaster sine korrekte loginoplysninger og trykker "Log ind"
- **Så** bliver brugeren automatisk omdirigeret til 2FA-konfigurationssiden uden at kunne tilgå andre dele af systemet

---

**AC2 — Adgangsbegrænsning under opsætning**

- **Givet** at brugeren er på 2FA-konfigurationssiden
- **Når** brugeren forsøger at tilgå andre sider i systemet
- **Så** mødes brugeren af en fejlmeddelelse, der beskriver at 2FA-konfigurationen skal færdiggøres, før man kan komme videre

---

**AC3 — Fejlhåndtering ved ugyldig 2FA-kode under opsætning**

- **Givet** at en bruger er i gang med at konfigurere 2FA og har indtastet fejlagtige informationer
- **Når** brugeren indsender 2FA-koden
- **Så** mødes brugeren af en fejlbesked med en forklaring på, hvad der gøres forkert, og har mulighed for at forsøge igen

---

**AC4 — 2FA-verificering ved efterfølgende login**

- **Givet** at en bruger har en korrekt opsat 2FA-konfiguration
- **Når** brugeren forsøger at logge ind
- **Så** skal brugeren anvende 2FA for at få adgang til systemet

---

**AC5 — Godkendt enhed huskes i 14 dage**

- **Givet** at en bruger logger ind med 2FA
- **Når** brugeren er logget succesfuldt ind
- **Så** gemmes enheden som en godkendt enhed, der automatisk bekræfter 2FA ved efterfølgende logins i de næste 14 dage

---

**AC6 — Trusted device udløber**

- **Givet** at en bruger har en godkendt enhed, og de 14 dage er udløbet
- **Når** brugeren forsøger at logge ind
- **Så** skal brugeren igen bekræfte med 2FA, og enheden opdateres med 14 nye dage ved succes

---

**AC7 — Login fra ny/ukendt enhed**

- **Givet** at en bruger har en godkendt enhed, men logger ind fra en anden enhed
- **Når** brugeren har indtastet korrekte loginoplysninger
- **Så** kræves 2FA-verificering uanset at andre enheder er godkendt

---

**AC8 — For mange fejlede 2FA-forsøg**

- **Givet** at en bruger gentagne gange indtaster forkert 2FA-kode
- **Når** brugeren har overskredet det maksimale antal forsøg
- **Så** låses kontoen midlertidigt, og brugeren informeres om, hvornår de kan forsøge igen

---

**AC9 — Mistet adgang til 2FA-app (gendannelse)**

- **Givet** at en bruger ikke kan tilgå sin 2FA-app
- **Når** brugeren befinder sig på 2FA-verificeringssiden
- **Så** har brugeren mulighed for at anvende en backup-kode som alternativ verificeringsmetode

---

**AC10 — Admin nulstiller brugers 2FA**

- **Givet** at en administrator nulstiller en brugers 2FA-konfiguration
- **Når** brugeren efterfølgende logger ind
- **Så** behandles brugeren som om de aldrig har haft 2FA opsat (jf. AC1)

---

**AC11 — Opsætningssession udløber**

- **Givet** at en bruger er i gang med 2FA-opsætning, men ikke fuldender den inden for en given tid
- **Når** opsætningssessionen udløber
- **Så** sendes brugeren tilbage til loginsiden og informeres om, at de skal starte forfra
