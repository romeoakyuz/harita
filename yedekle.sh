#!/bin/bash

cd /storage/emulated/0/MGİT/harita || { echo "Dizin bulunamadı!"; exit 1; }

git config --global --add safe.directory /storage/emulated/0/MGİT/harita

if [ ! -d ".git" ]; then
    git init
    git branch -M main
fi

if git remote | grep -q "origin"; then
    git remote set-url origin git@github.com:romeoakyuz/harita.git
else
    git remote add origin git@github.com:romeoakyuz/harita.git
fi

git add .

echo -n "Commit (yedek) adını yazın: "
read commitMessage

if [ -z "$commitMessage" ]; then
    commitMessage="otomatik yedek $(date +%Y-%m-%d_%H-%M-%S)"
fi

git commit -m "$commitMessage"
git push -u origin main --force
