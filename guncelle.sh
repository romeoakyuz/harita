#!/bin/bash
cd /storage/emulated/0/MGİT/harita

echo "----------------------------------------"
echo "Proje dizinine gidildi: /storage/emulated/0/MGİT/harita"
echo "----------------------------------------"

# Değişiklikleri ekle
git add .

# Kullanıcıdan commit mesajı al (boş bırakılırsa varsayılan atanır)
read -p "Commit mesajı girin (Enter'a basarsanız 'Harita guncellemesi' yazar): " msg
if [ -z "$msg" ]; then
  msg="Harita guncellemesi"
fi

# Commit yap
git commit -m "$msg"

echo "GitHub'a gönderiliyor (Push)..."
git push

echo "----------------------------------------"
echo " İşlem Tamamlandı! GitHub Actions APK'yı hazırlıyor."
echo "----------------------------------------"
