# Shorts Sayacı

Instagram Reels ve YouTube Shorts'ta kaç video kaydırdığını sayan Android uygulaması. Hızlı geçtiğin videolar da sayılır.

## Kurulum (telefondan)

1. Bu repoda **Releases → en son derleme → `ShortsSayaci.apk`** dosyasını indir.
2. APK'yı aç ve kur. Telefon "bilinmeyen kaynak" izni isterse ver.
3. Uygulamayı aç ve **Erişilebilirlik ayarlarını aç** düğmesine bas. Açılan ekranda *Shorts Sayacı*'nı etkinleştir.
   - Seçenek gri/kilitliyse **Ayarlar → Uygulamalar → Shorts Sayacı → ⋮ → Kısıtlanmış ayarlara izin ver** adımlarını izle, sonra tekrar dene.
4. Instagram veya YouTube'a gir. Sol üstte sayaç görünür.

## Nasıl çalışır

- Android'in **AccessibilityService** API'siyle sadece `TYPE_VIEW_SCROLLED` olaylarını dinler. Ekran içeriği kaydedilmez.
- Ekranı kaplayan dikey listede kaydırma durunca (~220 ms), o anki video sırası ile önceki sıra arasındaki fark sayılır.
  - Art arda hızlı kaydırmalar toplu olarak sayılır, kaçmaz.
  - Yarıda bırakılan kaydırma sayılmaz.
- Veriler sadece telefonda tutulur (SharedPreferences).

## Yanlış sayıyorsa

Uygulamada **Hata ayıklama günlüğü**nü aç, Reels/Shorts'ta birkaç kez kaydır. Günlükteki satırları (`id=... from=... to=...`) kopyalayıp gönder, filtreler o satırlara göre ayarlanır.

## Derleme

Her `main` push'unda GitHub Actions APK'yı derler ve yeni bir Release olarak yayınlar.
