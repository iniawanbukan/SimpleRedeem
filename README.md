# SimpleRedeem

Plugin Spigot/Paper untuk Minecraft yang memungkinkan pemain menukarkan kode redeem dengan hadiah. Admin dapat membuat dan mengelola kode, membatasi jumlah penggunaan, mengatur masa berlaku, dan menjadwalkan kode otomatis.

## Overview

* Kode dapat dibuat dengan nama sendiri atau dibuat secara acak.
* Hadiah berupa command console yang dijalankan saat kode ditukarkan.
* Kode dapat dibatasi jumlah penggunaannya, dinonaktifkan, diberi tanggal kedaluwarsa, atau dibuat berbasis permission.
* Secara default, setiap pemain hanya dapat menukarkan kode yang sama satu kali.
* Kode dan data pemain yang sudah menukarkan disimpan di `plugins/SimpleRedeem/codes.yml`.
* Kode otomatis dapat dijadwalkan harian, mingguan, atau satu kali, dengan pilihan webhook.
* Pengaturan utama, pesan, dan jadwal otomatis dapat disesuaikan melalui file YAML.

## Persyaratan

* Minecraft server versi 1.21 atau lebih baru (sesuai metadata plugin).
* Java 21.
* PlaceholderAPI bersifat opsional; placeholder pemain pada command hadiah akan diproses jika tersedia.

## Instalasi

1. Unduh atau build file plugin.
2. Letakkan `SimpleRedeem-1.0.0.jar` di folder `plugins` server.
3. Jalankan atau restart server.
4. Konfigurasi awal dibuat di `plugins/SimpleRedeem/`. Jalankan `/sr reload` setelah mengubah konfigurasi.

### Build dari source

Jalankan perintah berikut dari direktori proyek:

```sh
mvn clean package

```

File JAR hasil build tersedia di folder `target/`.

## Commands

### Pemain

| Command | Keterangan |
| --- | --- |
| `/redeem <kode>` | Menukarkan kode dan menerima hadiahnya. Hanya dapat dijalankan oleh pemain. |

### Admin

`/sr` juga dapat dipanggil menggunakan alias `/simpleredeem`.

| Command | Keterangan |
| --- | --- |
| `/sr help` | Menampilkan daftar subcommand. |
| `/sr new <panjang|kode>` | Membuat kode acak dengan panjang yang diminta, atau membuat kode dengan nama yang diberikan. |
| `/sr delete <kode>` | Menghapus kode. |
| `/sr info <kode>` | Melihat status, expiry, jumlah penggunaan, dan command reward kode. |
| `/sr addcmd <kode> <command>` | Menambahkan command console sebagai hadiah. |
| `/sr delcmd <kode> <nomor>` | Menghapus command reward berdasarkan nomor yang ditampilkan di info. |
| `/sr uses <kode> <jumlah|infinite>` | Mengatur batas penggunaan; `infinite` berarti tanpa batas. |
| `/sr bulk <kode> <jumlah>` | Membuat salinan kode beserta pengaturan dan reward-nya. |
| `/sr permbased <kode> <true|false>` | Mengaktifkan atau menonaktifkan syarat permission khusus untuk kode. |
| `/sr enabled <kode> <true|false>` | Mengaktifkan atau menonaktifkan kode. |
| `/sr expiry <kode> <dd/MM/yyyy|none>` | Mengatur tanggal kedaluwarsa atau menghapusnya dengan `none`. |
| `/sr debug` | Mengaktifkan atau menonaktifkan debug mode. |
| `/sr version` | Menampilkan versi plugin. |
| `/sr debuginfo` | Menampilkan status debug dan jumlah kode yang dimuat. |
| `/sr reload` | Memuat ulang konfigurasi, data kode, dan jadwal. |

### Format reward command

Command reward dijalankan oleh console. Placeholder yang tersedia:

* `{PLAYER}` atau `%player%` — nama pemain.

## Permissions

Semua permission admin di bawah ini secara default diberikan kepada operator. `sr.admin` memberikan akses penuh ke seluruh subcommand admin.

| Permission | Fungsi |
| --- | --- |
| `sr.admin` | Akses penuh admin. |
| `sr.admin.create` | Permission create yang dideklarasikan plugin. |
| `sr.admin.delete` | Menghapus kode. |
| `sr.admin.info` | Melihat informasi kode. |
| `sr.admin.addcmd` | Menambahkan command reward. |
| `sr.admin.delcmd` | Menghapus command reward. |
| `sr.admin.uses` | Mengubah batas penggunaan. |
| `sr.admin.bulk` | Menyalin kode secara massal. |
| `sr.admin.permbased` | Mengubah mode permission-based. |
| `sr.admin.enabled` | Mengaktifkan atau menonaktifkan kode. |
| `sr.admin.expiry` | Mengatur masa berlaku kode. |
| `sr.admin.debug` | Mengubah debug mode. |
| `sr.admin.help` | Melihat bantuan. |
| `sr.admin.reload` | Memuat ulang plugin. |
| `sr.admin.version` | Melihat versi plugin. |
| `sr.admin.debuginfo` | Melihat informasi debug. |
| `sr.user.redeem` | Dideklarasikan untuk pemain dengan default `true`, tetapi tidak digunakan sebagai pemeriksaan akses umum oleh command `/redeem`. |

**Catatan permission:** Ada perbedaan nama pada implementasi subcommand pembuatan kode: pemeriksaan `/sr new` menggunakan `sr.admin.new`, sedangkan plugin mendeklarasikan `sr.admin.create`. Untuk memberikan akses admin tanpa kendala ini, gunakan `sr.admin`; jika mengatur akses granular, berikan node `sr.admin.new` untuk pembuatan kode.

Jika kode diaktifkan sebagai permission-based, pemain juga harus memiliki:

```text
sr.user.redeem.<kode-huruf-kecil>

```

Contoh: kode `WELCOME` memerlukan `sr.user.redeem.welcome`.

## Konfigurasi

File konfigurasi berada di `plugins/SimpleRedeem/`:

* `config.yml` — sensitivitas huruf kode, batas panjang, aturan satu penukaran per pemain, broadcast, dan debug mode.
* `message.yml` — prefix dan teks pesan plugin.
* `auto.yml` — jadwal kode otomatis, zona waktu, dan pengaturan webhook.
* `codes.yml` — dibuat dan dikelola plugin untuk menyimpan kode, reward, penggunaan, dan pemain yang sudah menukarkan.

Pengaturan default yang penting di `config.yml`:

| Pengaturan | Default | Arti |
| --- | --- | --- |
| `case-sensitive` | `false` | Kode tidak membedakan huruf besar dan kecil. |
| `one-redeem-per-player` | `true` | Pemain hanya dapat menukarkan kode yang sama satu kali. |
| `max-code-length` | `32` | Panjang maksimum kode. |
| `broadcast-on-redeem` | `false` | Mengumumkan penukaran kode kepada server. |
| `debug-mode` | `false` | Mengaktifkan mode debug. Dalam mode ini pemeriksaan penukaran dan pengurangan jumlah penggunaan dilewati. |

Jadwal otomatis di `auto.yml` dinonaktifkan secara default. Aktifkan `enabled`, lalu atur entri `schedules` dengan `type` (`daily`, `weekly`, atau `once`), waktu, kode, jumlah penggunaan, dan reward. Webhook juga dinonaktifkan secara default; isi URL hanya jika ingin mengirim notifikasi ke endpoint webhook.

## Credit

Dibuat oleh **ChairawanChannel**.