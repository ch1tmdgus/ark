<p align="center">
  <a href="README.md">English</a> · <b>한국어</b> · <a href="README.ja.md">日本語</a>
</p>

# ARK

**ARK**는 루트 권한으로 동작하는 안드로이드 백업·복원 앱입니다. 앱의 APK(스플릿 APK 포함)와 앱 데이터를 함께 묶어서, 기기 내부, 원하는 폴더, 또는 직접 운영하는 WebDAV 클라우드에 저장합니다.

- 패키지명: `app.n4c.ark`
- 최소 SDK: 26 (Android 8.0), 대상 SDK 34
- UI: Jetpack Compose + Material 3
- 지원 언어: English, 한국어, 日本語 (시스템 언어를 따라감)

> **루트 권한이 필요합니다.** ARK는 `/data/data`에 있는 다른 앱의 개인 데이터를 읽는데, 이는 슈퍼유저 권한이 있어야만 가능합니다.

## 주요 기능

- **유연한 백업** – APK만, 데이터만, 또는 둘 다 선택할 수 있습니다.
- **세 가지 저장 위치**
  - 이 기기 (`/sdcard/ARK`)
  - 직접 고른 폴더 (Storage Access Framework)
  - WebDAV 클라우드 (설정에서 클라우드를 연결했을 때만 표시)
- **백그라운드 동작** – 포그라운드 서비스로 실행되어 진행률(%)과 로그 한 줄을 보여주고, 결과는 알림으로 알려주므로 앱을 닫아도 됩니다.
- **백업 메타데이터** – 백업한 기기, 안드로이드 버전, 앱 버전, 날짜와 시간을 기록하고 복원 전에 보여줍니다.
- **간편한 복원** – 복원 위치(이 기기, 클라우드, 직접 선택한 파일)를 고르고 백업을 누르기만 하면 됩니다. APK를 다시 설치하고 데이터를 되돌립니다.
- **첫 실행 안내** – 환영 화면, 루트·권한 확인을 거쳐 바로 시작할 수 있으며, 한 번만 표시됩니다.

## 요구 사항

- 루팅된 기기 (Magisk, KernelSU, APatch 등)
- 긴 백업을 위해 알림 권한과 배터리 제한 해제를 권장합니다
- 소스에서 빌드하려면 Android Studio (Koala 이상)와 JDK 17

## 빌드

```bash
git clone https://github.com/ch1tmdgus/ark.git
cd ark
```

1. Android Studio에서 폴더를 열고 Gradle Sync가 끝나길 기다립니다.
2. USB 디버깅을 켠 루팅된 폰을 연결하고 **Run**을 누르거나, **Build ▸ Build APK(s)**로 APK를 만듭니다.
3. 처음 실행하면 루트 관리 앱에서 ARK에 슈퍼유저 권한을 허용합니다.

의존성은 Google, Maven Central, JitPack(libsu)에서 받아옵니다.

## 사용법

1. **백업** – 앱을 누르고, 백업할 내용을 고른 다음, 저장 위치를 고릅니다.
2. **복원** – **복원** 버튼을 누르고 복원 위치를 고른 뒤, 백업을 선택하고 확인합니다.
3. **클라우드** – **설정**(톱니바퀴)에서 WebDAV 주소, 사용자명, (앱) 비밀번호를 입력하고 **연결**을 누릅니다.

WebDAV 주소 예시:

| 서버 | 주소 |
| --- | --- |
| Nextcloud | `https://example.com/remote.php/dav/files/사용자명` |
| Synology WebDAV Server | `https://NAS주소:5006` (HTTP는 `5005`) |

백업은 서버의 `ARK` 폴더에 저장됩니다. HTTP Basic 인증만 지원하며, 2단계 인증을 쓰는 서비스는 앱 비밀번호를 발급해서 사용하세요.

## 백업 형식

백업 하나는 이름이 같은 두 파일로 이루어집니다.

```
<패키지명>_<yyyyMMdd_HHmmss>.ark    # tar 아카이브
├── meta.json                       # 기기, 안드로이드 버전, 앱 버전, 날짜, 포함 내용
├── apk/                            # base.apk 및 스플릿 APK   (APK / APK + 데이터)
├── data.tar                        # /data/data/<패키지명>    (데이터 / APK + 데이터)
└── ext.tar                         # /sdcard/Android/data/<패키지명> (있을 때만)

<패키지명>_<yyyyMMdd_HHmmss>.json   # 목록을 빠르게 불러오기 위한 meta.json 사본
```

## 동작 원리

ARK는 글로벌 마운트 네임스페이스에서 실행되는 루트 셸([libsu](https://github.com/topjohnwu/libsu))로 시스템과 통신하므로 모든 앱의 `/data/data`가 보입니다. 데이터는 `tar`로 묶고, APK는 `pm install-create / install-write / install-commit`으로 설치하며, 데이터를 복원한 뒤에는 `chown`으로 소유권을, `restorecon`으로 SELinux 컨텍스트를 맞춥니다. WebDAV는 [OkHttp](https://square.github.io/okhttp/)로 구현했습니다.

## 제한 사항

- 데이터가 기기에 묶여 있는 앱(Android Keystore, 하드웨어 기반 인증 정보, 일부 은행 앱)은 복원 후 동작하지 않을 수 있습니다.
- 런타임 권한은 백업에 포함되지 않아서 복원 후 다시 허용해야 할 수 있습니다.
- 백업 파일은 **암호화되지 않습니다.** 로그인 세션 같은 민감한 데이터가 들어 있을 수 있으니 클라우드에 저장한 파일을 잘 보호하세요.
- 복원하면 앱의 현재 데이터가 **덮어써집니다.**

## 면책

루트 권한으로 앱 데이터를 백업·복원하면 데이터가 손실되거나 앱이 고장날 수 있습니다. 사용에 따른 책임은 사용자에게 있으며, 먼저 중요하지 않은 앱으로 테스트해보세요.

## 프로젝트 구조

```
app/src/main/java/app/n4c/ark/
├── MainActivity.kt      # 진입점, 테마, 온보딩/홈 전환
├── Onboarding.kt        # 첫 실행 환영 및 권한 페이지
├── Screens.kt           # 홈 화면과 다이얼로그
├── Sheets.kt            # 백업·복원·설정 바텀시트
├── Components.kt        # 공용 UI 컴포넌트
├── Theme.kt             # Material 3 색상 스킴
├── BackupEngine.kt      # 루트 백업/복원 로직
├── ArkService.kt        # 포그라운드 서비스와 공유 작업 상태
├── WebDavClient.kt      # WebDAV 클라이언트
├── Prefs.kt, Models.kt, Utils.kt
```

## 사용한 오픈소스

- [libsu](https://github.com/topjohnwu/libsu) (topjohnwu)
- [OkHttp](https://github.com/square/okhttp) (Square)
