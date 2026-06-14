# kurek.ytueks.com — VPS Kurulum

## 1. Projeyi VPS'e gönder

```bash
# kurek klasöründe proje dosyalarını topla
cd /home/rugzo/training/rowing-reservation-system

# Sadece gereken dosyaları VPS'e tarla
scp docker-compose.yml root@194.163.161.69:/root/kurek/
scp docker-compose.prod.yml root@194.163.161.69:/root/kurek/
scp .env root@194.163.161.69:/root/kurek/
```

## 2. VPS'de başlat

```bash
ssh root@194.163.161.69
cd /root/kurek
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

## 3. Logları kontrol et

```bash
docker compose logs -f
```

Çıktıda şunları görmelisin:
- PostgreSQL: `database system is ready to accept connections`
- Backend: `Started RowingBackendApplication`
- Frontend: `Listening on port 3001`

## 4. Demo hesaplar

| Rol | Email | Şifre |
|---|---|---|
| Superadmin | superadmin@rowingclub.com | superadmin123 |
| Club Admin | admin@riversiderowingclub.com | admin123 |
| Trainer | trainer1@riversiderowingclub.com | trainer123 |
| Member | member1@riversiderowingclub.com | member123 |

## 5. Nginx (Salih'e ilet)

Mevcut yapılandırmada `/api/*` istekleri backend'e (8081) yönlendirilmeli:

```nginx
location /api/ {
    proxy_pass http://localhost:8081;
    ...
}

location / {
    proxy_pass http://localhost:3001;
    ...
}
```

## Sık kullanılan komutlar

```bash
docker compose logs -f                    # canlı log
docker compose restart backend            # backend'i yeniden başlat
docker compose up -d --build frontend     # frontend'i yeniden build et
docker compose down                       # durdur
docker compose down -v && docker compose up -d  # sıfırdan başlat (DB silinir)
```
