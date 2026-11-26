# Değişiklik Günlüğü

## v1.4.1 (derleme 7)
- Aynı isim kontrolü kaldırıldı. Aynı isimli kullanıcılar kimlikleriyle ayrılıyor.

## v1.4 (derleme 6)
- Takma ad ilk katılımda bir kez seçiliyor. Seçtikten sonra isim kutusu gizleniyor, isim 🔒 kalıcı oluyor.
- İsim seçmeden önce onay penceresi çıkıyor.

## v1.3 (derleme 5)
- **🏆 Sıralama sekmesi:** Firebase üzerinden online sıralama.
  - Ağa sadece "Verilerimi gönder ve sıralamayı güncelle" butonuna basınca çıkılıyor, canlı veri akışı yok.
  - Sadece özet gönderiliyor: takma ad, toplamlar ve rekorlar. Anonim giriş kullanılıyor, hesap gerekmiyor.
  - 7 ödül kategorisi: 🏆 Kaydırma Kralı, 📅 Haftanın Yıldızı, ⚡ Şimşek Parmak, ⏱ Maratoncu, 🎯 Oturum Canavarı, 🔥 Rekor Gün, 🧘 Zen Ustası.
  - "Ödüllerin" kartı: her kategoride kaçıncı olduğun ve kazandığın madalyalar.
- Üstte **📊 İstatistikler | 🏆 Sıralama** sekmeleri.
- (Derleme 4: sıralama kodu, sunucu ayarı olmadan.)

## v1.2 (derleme 3)
- Yeni koyu arayüz ve kart tasarımı.
- Bugün kartı: dünle ve ortalamayla karşılaştırma, Instagram/YouTube oran çubuğu.
- **7 gün / 30 gün / 90 gün / Tümü** aralık seçici.
- Yeni istatistikler: video başına süre, kaydırma hızı (video/dk), oturumlar, en uzun oturum, hızlı geçiş oranı.
- "Bir videoda ne kadar kalıyorsun?" dağılımı (<2 sn … 60 sn+).
- Saatler kartı: en aktif saat, en yoğun 3 saat, gece kaydırma oranı.
- Haftanın günleri grafiği, hafta içi ve hafta sonu karşılaştırması.
- "Öne çıkanlar": haftalık değişim, yıllık projeksiyon ve benzeri.
- Eski veriler korunuyor.

## v1.1 (derleme 2)
- Son 30 gün grafiği, saatlik dağılım, izleme süresi.
- İstatistikler: günlük ortalama, en çok gün, bu hafta, bu ay, toplam süre.
- Aylara göre gruplanmış tüm geçmiş.

## v1.0 (derleme 1)
- İlk sürüm: Instagram Reels ve YouTube Shorts kaydırma sayacı (AccessibilityService).
- Hızlı geçilen videolar da sayılıyor, yarıda bırakılan kaydırmalar sayılmıyor.
- Ekranda küçük sayaç, son 7 gün, hata ayıklama günlüğü.
