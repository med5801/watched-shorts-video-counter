# Shorts Sayacı

Instagram Reels ve YouTube Shorts'ta kaç video kaydırdığını sayan Android uygulaması. Hızlı geçtiğin videolar da sayılır. Detaylı istatistikler ve arkadaşlarınla isteğe bağlı online sıralama içerir.

📥 **İndir:** [Releases → en son sürüm → `ShortsSayaci.apk`](../../releases/latest) · 📝 [Değişiklik günlüğü](CHANGELOG.md)

## Özellikler

**📊 İstatistikler**
- Bugünün sayısı, dünle ve ortalamayla karşılaştırma, Instagram/YouTube oranı
- 7 gün / 30 gün / 90 gün / Tümü aralık seçici
- Toplam video ve süre, günlük ortalama, video başına süre, kaydırma hızı
- Oturumlar ve en uzun oturum, hızlı geçiş oranı
- Grafikler: günlük kaydırma, saatlere göre, haftanın günleri, video başına izleme süresi dağılımı
- Öne çıkanlar ve aylara göre geçmiş

**🏆 Sıralama**
- Butona bastığında özetin gönderilir ve liste bir kez çekilir. Arka planda veri akışı yok.
- Takma ad bir kez seçilir. Hesap gerekmez (anonim giriş).
- 7 ödül kategorisi: Kaydırma Kralı, Haftanın Yıldızı, Şimşek Parmak, Maratoncu, Oturum Canavarı, Rekor Gün, Zen Ustası

## Kurulum

1. Releases'tan `ShortsSayaci.apk` dosyasını indirip kur.
   - Play Protect engellerse: Play Store → profil → Play Protect → ⚙️ → "Uygulamaları tara"yı kurulum süresince kapat.
2. Uygulamayı aç ve **Erişilebilirlik ayarlarını aç**'a bas. *Shorts Sayacı*'nı etkinleştir.
   - Gri/kilitliyse önce açmayı dene, sonra **Ayarlar → Uygulamalar → Shorts Sayacı → ⋮ → Kısıtlanmış ayarlara izin ver**.
3. Güncellemeleri eski sürümü silmeden üstüne kur, verilerin korunur.

## Nasıl çalışır

- Android'in **AccessibilityService** API'siyle sadece kaydırma olaylarını dinler. Ekran içeriği kaydedilmez.
- Ekranı kaplayan dikey listede kaydırma durunca o anki video sırası ile önceki sıra arasındaki fark sayılır.
- İstatistikler sadece telefonda tutulur. Sıralamaya sadece özet gider (Firebase Firestore).

## Yanlış sayıyorsa

Ayarlardan **Hata ayıklama günlüğü**nü aç, Reels/Shorts'ta birkaç kez kaydır ve günlükteki satırları gönder.

## Derleme

Her `main` push'unda GitHub Actions APK'yı derler ve Releases'a yeni sürüm olarak koyar (`.github/workflows/build.yml`).
Sunucu kuralları `firestore.rules` dosyasında. Uçtan uca sunucu testi için Actions sekmesinden **Sunucu testi**'ni çalıştırabilirsin.
