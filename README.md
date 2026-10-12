# Polyfield → Minecraft (Fabric 1.20.1)

Polyfield'ın (com.MA.Polyfield 0.7.5) silahları, askerleri ve tankları Minecraft'ta —
**oyunun kendi modelleri, dokuları ve sesleriyle**.

* 16 silah: Kar98k, M1 Garand, MP40, Thompson M1A1, StG 44, Sten, MG42, MG34, BAR, M3 Grease Gun,
  VG 1-5, Walther P38, M1915, Welrod, Panzerfaust, Bazooka. Hasar, atış hızı, şarjör, yedek mermi
  ve geri tepme değerleri Polyfield'ın kendi `Weapon` verisinden okundu.
* **Sol tık** ateş (otomatik silahlarda basılı tut), **sağ tık** nişan (zoom), **R** şarjör değiştir.
* Alman ve Müttefik Polyfield askerleri gece (bazen gündüz) açık arazide 2-4 kişilik timler halinde
  doğar, oyuncuya ve karşı tarafa ateş eder, şarjör değiştirir, el bombası atar.
* Sürülebilir tanklar: Sherman Firefly ve Jagdpanther (WASD sür, fareyle taret, sol tık top).
* M24 / Mk 2 el bombaları, roketler, Polyfield patlama sesleri; isabet ve kafadan vuruş işaretleri.
* Türkçe ve İngilizce dil dosyaları.

## Varlıklar (modeller ve sesler) nereden geliyor?

Depoda **hiçbir Polyfield dosyası yoktur**. `converter/extract_polyfield.py`, kendi Polyfield
kopyanızdan (APK/APKS) UnityPy ve fsb5 ile modelleri, dokuları ve sesleri çıkarıp
`build/polyfield-assets/` içine Minecraft kaynakları olarak yazar; derleme bunları jar'a ekler.
Bu jar kişisel kullanım içindir, paylaşmayın.

```bash
pip install UnityPy py7zr fsb5 numpy pillow   # fsb5 kurulmazsa: pip download fsb5, fsb5/ klasörünü site-packages'e kopyalayın
# ffmpeg gerekir (sesleri mono OGG'ye çevirmek için)
python3 -I converter/extract_polyfield.py --input com.MA.Polyfield_0.7.5.apks --out build/polyfield-assets \
        --preview build/polyfield-preview
gradle build            # build/libs/polyfield-1.0.0.jar
```

Varlıklar olmadan da mod derlenir ve çalışır (modeller görünmez, sesler çalmaz).

## Kurulum

Minecraft 1.20.1 + Fabric Loader ≥ 0.14.21 + Fabric API. Jar'ı `mods/` klasörüne koyun.

## Polyfield haritası (Normandiya) Minecraft içinde

Polyfield'ın Normandiya haritası (arazi, sahil, siperler, bunkerler, köy evleri, kirpi engelleri,
Higgins botları) oyunun kendi modelleri ve dokularıyla çizilir; altına görünmez çarpışma blokları döşenir.

1. Yeni bir dünya: **Süperdüz → "Boşluk" (The Void)** hazır ayarı, hileler açık.
2. `/polyfield map build` — haritayı (0, 64, 0) çevresine kurar (~750×750 m, birkaç saniye sürer).
3. `/polyfield map tp` — sahile (Müttefik çıkarması) ışınlar; `/polyfield map tp german` Alman tarafına.
4. `/polyfield map battle` — bayraklara Alman timleri, sahile Müttefik askerleri ve bir Sherman koyar.

## Oyun kuralları ve komutlar

| Kural | Varsayılan | Anlamı |
|---|---|---|
| `pfInfiniteAmmo` | false | Şarjör değiştirmek mermi eşyası harcamaz |
| `pfSoldierSpawning` | true | Askerler doğal olarak doğar |
| `pfExplosivesBreakBlocks` | true | Oyuncu roketleri/el bombaları blok kırar |

`/polyfield squad german|allied [sayı]` önünüze bir tim çağırır (OP).

Mermiler: tabanca/makineli (demir + barut + bakır → 16), tüfek (→ 12), MG şeridi (→ 50), roket.
Tüm silah, mermi, el bombası ve tank tarifleri yaratıcı sekmesinde "Polyfield" altında.

## Teknik notlar

* Unity → Minecraft: X ekseni ters çevrilir, V çevrilir, üçgen sırası tersine çevrilir.
  Statik modeller `PFM1`, iskeletli asker `PFS1` ikili formatında (bkz. `PfModel`, `PfSkinnedModel`).
* Polyfield'ın animasyonları Mecanim humanoid kas eğrileri olduğundan asker pozu prosedüreldir:
  yürüyüş döngüsü, nişan açısı ve iki kemikli IK ile silahı tutan kollar (CPU skinning).
* Otomatik silahlar Polyfield'daki gibi döngü sesi + kuyruk sesiyle çalar.
* IL2CPP oyununda typetree yok; `Weapon` alanlarının adları/sırası `global-metadata.dat`'tan,
  değerler ham MonoBehaviour verisinden okunur (`converter/weapon_stats.py`).

Kod: MIT. Polyfield modelleri, dokuları ve sesleri sahiplerine aittir.
