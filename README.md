# 어디서돈썼지도 — 결제 위치 검증 앱

정식 앱 개발 전에 아래 가설을 검증하기 위한 **데이터 수집 도구**입니다.

> 토스 결제 알림을 받은 시점의 기기 위치가 실제 결제 매장에서 100m 이내인가?

팀원 4명이 실제 결제 40건 이상을 기록하고 CSV로 합쳐서 분석합니다. 서버가 없으므로 모든 데이터는 각자의 기기에만 저장됩니다.

---

## 0. APK 받기 (팀원은 여기만 보면 됩니다)

폰 브라우저에서 아래 링크를 열면 최신 APK가 내려받아집니다. 설치는 [2. 폰에 설치](#2-폰에-설치)의 방법 2를 따르세요.

**https://github.com/LSU1101/MobileProgramming/releases/latest/download/app-debug.apk**

- 지난 버전은 [Releases](https://github.com/LSU1101/MobileProgramming/releases) 페이지에 있습니다.
- 저장소가 비공개라면 GitHub에 로그인한 상태여야 받을 수 있습니다.

### 새 버전 배포하기 (Android Studio 불필요)
APK는 GitHub Actions가 빌드합니다. 아래 둘 중 하나를 하면 몇 분 뒤 Release에 새 APK가 올라갑니다.
- **버튼으로:** 저장소 → **Actions** → 왼쪽 **Android** → **Run workflow** (`main` 선택) → 실행
- **태그로:** `git tag v0.2.0 && git push origin v0.2.0`

빌드할 때마다 versionCode가 올라가고, 모든 빌드가 저장소의 공용 debug 키(`app/debug.keystore`)로 서명됩니다. 그래서 새 APK를 **기존 앱 위에 그대로 덮어 설치**할 수 있고 데이터도 유지됩니다.

`main`에 푸시하거나 PR을 올릴 때도 같은 워크플로가 단위 테스트와 APK 빌드를 검사합니다. 이때는 Release를 만들지 않습니다.

---

## 1. APK 직접 빌드 (개발자용)

### 준비물
- **Android Studio** 최신 안정 버전 (JDK 17 이상 포함)
- 또는 커맨드라인으로 빌드할 경우: JDK 17+와 Android SDK (Platform 36, Build-Tools)

### A. Android Studio로 빌드 (권장)
1. Android Studio → **Open** → 이 저장소 폴더를 선택합니다.
2. Gradle Sync가 끝날 때까지 기다립니다. SDK 36이 없다고 나오면 안내에 따라 설치하세요.
3. 메뉴 **Build → Generate App Bundles or APKs → Generate APKs**를 실행합니다. (구버전 메뉴명: Build → Build Bundle(s) / APK(s) → Build APK(s))
4. 완료 알림의 **locate**를 누르면 `app/build/outputs/apk/debug/app-debug.apk`가 열립니다.

> Android Studio가 AGP나 Kotlin 버전을 올리라고 제안하면 그대로 따라도 됩니다.

### B. 커맨드라인으로 빌드
```bash
# SDK 위치 지정 (둘 중 하나)
export ANDROID_HOME=$HOME/Android/Sdk           # macOS: $HOME/Library/Android/sdk
# 또는 echo "sdk.dir=/path/to/Android/Sdk" > local.properties

./gradlew testDebugUnitTest   # 파서·거리·CSV 단위 테스트
./gradlew assembleDebug       # → app/build/outputs/apk/debug/app-debug.apk
```
Windows에서는 `gradlew.bat assembleDebug`를 사용합니다.

---

## 2. 폰에 설치

### 방법 1 — USB + adb (권장)
1. 폰에서 **설정 → 휴대전화 정보 → 소프트웨어 정보 → 빌드번호**를 7번 눌러 개발자 옵션을 켭니다.
2. **개발자 옵션 → USB 디버깅**을 켜고 PC에 연결한 뒤, 폰에 뜨는 허용 창에서 승인합니다.
3. PC에서 설치합니다.
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
   Android Studio에서 폰을 선택하고 ▶ Run을 눌러도 됩니다.

### 방법 2 — APK 파일을 직접 전달
1. `app-debug.apk`를 카카오톡, 구글 드라이브 등으로 폰에 보냅니다.
2. 파일을 열고 "출처를 알 수 없는 앱 설치"를 허용해 설치합니다.
3. ⚠️ **Android 13 이상**에서는 이렇게 설치한 앱의 알림 접근이 **"제한된 설정"**으로 막힙니다. 다음 순서로 풀어 주세요.
   - **설정 → 애플리케이션 → 어디서돈썼지도 검증 → 우측 상단 ⋮ → "제한된 설정 허용"**
   - 그 다음 앱의 권한 화면에서 알림 접근을 켭니다.
   - `adb install`로 설치하면 이 단계가 필요 없습니다.

> 모든 빌드(로컬, CI)가 저장소의 공용 debug 키로 서명되므로 새 버전을 덮어 설치해도 데이터가 유지됩니다. 단, **앱을 삭제하면 데이터가 사라지니** 재설치나 삭제 전에는 반드시 CSV를 먼저 내보내세요.

---

## 3. 처음 설정 (앱의 "권한" 화면)

위에서부터 순서대로 모두 ✅로 만듭니다.

| 순서 | 항목 | 설정 방법 |
|---|---|---|
| ① | 알림 접근 | 목록에서 "어디서돈썼지도 검증"을 켭니다 |
| ② | 위치 (정확한 위치) | "앱 사용 중에만 허용"을 고르고 **"정확한 위치" 스위치를 켭니다** |
| ③ | 백그라운드 위치 | Android 11 이상은 설정 화면이 열리면 **"항상 허용"**을 선택합니다 |
| ④ | 배터리 최적화 제외 | "허용"을 선택합니다 |

추가로 다음을 확인합니다.
- **토스 앱의 결제 알림이 켜져 있어야 합니다.** (토스 → 전체 → 설정 → 알림)
- **삼성:** 설정 → 배터리 → 백그라운드 사용 제한에서 이 앱이 "절전 상태 앱"이나 "사용하지 않는 앱"에 들어 있으면 빼 주세요.
- **샤오미/기타:** "자동 시작"과 "배터리 제한 없음"을 설정합니다.
- **설정 화면:** 팀원 이름을 입력하고 저장합니다. CSV의 `member` 열에 들어갑니다.

### 토스 패키지명 확인 (최초 1회)
토스 패키지명은 `viva.republica.toss`로 추정하고 있지만 확실하지 않습니다.
1. **설정 → "모든 앱 알림 기록"**을 켭니다.
2. 토스 알림을 하나 받은 뒤 **설정 → "수신된 패키지"** 목록에서 토스로 보이는 패키지를 확인합니다. 기본값과 다르면 **"대상 추가"**를 누릅니다.
3. 확인이 끝나면 토글을 끄고, "대상 앱이 아닌 기록 모두 삭제"로 정리합니다.

---

## 4. 결제 1건 기록 절차

1. **평소대로 결제합니다.** 앱은 켜 두지 않아도 됩니다. 앱이 꺼진 상태에서도 되는지가 핵심 검증 항목입니다.
2. 토스 알림이 오면 앱이 원본 알림을 저장하고 위치를 1회(최대 10초) 측정합니다.
3. **매장 앞에 있는 동안** 앱을 열고 해당 기록 → **"지금 매장 앞에서 위치 찍기"**를 누릅니다. 이 좌표가 기준 좌표가 됩니다.
4. 나중에 토스 거래내역을 보고 다음을 입력한 뒤 **저장**합니다.
   - **실제 결제 시각**
   - **실제 매장명**
   - **온라인/오프라인**
   - (선택) 메모
5. 목록 상단의 진행 현황에서 "100m 이내" 건수를 볼 수 있습니다.

> 온라인 결제는 매장 위치가 없으므로 "온라인"으로 표시만 하고 기준 좌표는 찍지 않아도 됩니다.

## 4-1. 결제 없이 위치만 검증하기 (테스트 알림)

실제 결제 없이 "앱이 꺼진 상태에서 알림 시점의 위치가 매장에서 100m 이내인가"를 반복해서 시험할 수 있습니다.

1. 매장 입구 앞에서 앱 상단의 **테스트**를 누릅니다.
2. 장소 메모(예: `강남역 지하상가 B1 올리브영`)와 대기 시간(30초, 1분, 3분)을 고르고 **"기준 위치 찍고 테스트 알림 예약"**을 누릅니다.
   - 앱이 이 순간 기준 좌표를 찍어 둡니다.
3. 예약되면 바로 **최근 앱 목록에서 앱을 지우고 화면을 끈 뒤** 폰을 주머니에 넣습니다.
4. 예약 시각에 "카드 알림 받기 / 1,000원 결제" 테스트 알림이 오면, 토스 알림과 똑같이 위치를 측정해 기록합니다.
   - 알림이 올 때까지 그 자리에 있어야 합니다.
5. 목록의 **테스트** 탭에서 거리와 100m 이내 여부를 바로 확인합니다.

- 처음 쓸 때 테스트 화면에서 **알림 표시 허용**과 **알람 및 리마인더 허용**(Android 12 이상)을 해 주세요. 알람 및 리마인더 권한이 없으면 알림이 몇 분 늦게 올 수 있습니다.
- CSV에서는 `is_test = true`로 구분됩니다. `test_alarm_delay_sec`는 예약 시각보다 알림이 얼마나 늦게 왔는지입니다.
- 한계: 테스트 알림은 이 앱이 직접 띄우므로, "꺼져 있던 앱을 시스템이 깨워 토스 알림을 전달하는" 상황과 100% 같지는 않습니다. 실제 토스 알림 지연과 형식 확인은 평소 결제로 보완하세요.

## 5. CSV 내보내기와 합치기
- 목록 화면 우측 상단의 **CSV** 버튼을 누르고 공유 시트에서 카톡, 드라이브, 메일 등을 고릅니다.
- 파일 이름: `wherepaid_<이름>_<날짜시각>.csv` (UTF-8 BOM이라 엑셀에서 바로 열립니다)
- 4명의 파일은 헤더 행만 빼고 이어붙이면 됩니다. `member` 열로 사람을 구분합니다.

### 주요 분석 컬럼
| 컬럼 | 의미 |
|---|---|
| `notif_delay_sec` | 알림 지연 = 수신 시각 − 실제 결제 시각 (초) |
| `post_delay_sec` | 알림 postTime − 실제 결제 시각 (초) |
| `distance_m`, `within_100m` | 알림 수신 좌표 ↔ 매장 앞 기준 좌표 거리, 그리고 100m 이내 여부 |
| `last_known_distance_m` | 새 측정이 아닌 캐시 좌표(getLastLocation)를 썼다면 거리가 얼마였을지 |
| `loc_status`, `loc_fail_reason` | 위치 성공/실패 여부와 실패 원인 (아래 표) |
| `accuracy_m`, `loc_elapsed_ms`, `loc_fix_age_ms` | 정확도, 획득 소요 시간, 좌표의 신선도 |
| `perm_background`, `app_importance_label`, `battery_opt_ignored`, `device_idle_mode` | 수신 시점의 권한과 앱·전원 상태 |
| `notification_key` | 같은 key가 여러 행이면 같은 알림이 중복 또는 업데이트로 수신된 것 |
| `parsed_kind` | 알림 종류: `PAYMENT`(결제), `CANCEL`(취소), `FAILED`(결제 실패: 잔액 부족, 승인 거절 등), `POINT`(포인트 적립), `OTHER`(기타). **결제 분석은 PAYMENT, CANCEL만** 사용. FAILED는 돈이 나가지 않았지만 매장에서 결제를 시도한 기록이라 위치 검증에는 쓸 수 있음 |
| `app_version` | 기록한 앱 버전 `versionName(versionCode)`. 측정 방식이 바뀐 빌드 전후 데이터를 나눌 때 사용 |

### 위치 실패 원인 (`loc_fail_reason`)
| 값 | 의미 |
|---|---|
| `NO_LOCATION_PERMISSION` | 위치 권한 자체가 없음 |
| `NO_BACKGROUND_PERMISSION_WHILE_BACKGROUND` | 앱이 백그라운드였고 "항상 허용"이 아니어서 결과를 얻지 못함 |
| `LOCATION_DISABLED` | 기기 위치 서비스가 꺼져 있음 |
| `TIMEOUT_10S` | 10초 안에 새 좌표를 얻지 못함 |
| `NULL_RESULT` | 그 밖의 이유로 결과가 없음 |
| `SECURITY_EXCEPTION`, `PLAY_SERVICES_ERROR` | 예외 발생 (`loc_error`에 메시지가 기록됨) |
| `INTERRUPTED` | 측정 도중 앱 프로세스가 종료됨 |

---

## 6. 개발 참고

### 구조
```
app/src/main/java/com/wherepaid/validator/
├── capture/PaymentNotificationListener.kt  알림 수신 → 원본 저장 → 위치 1회 요청
├── capture/LocationCapturer.kt             getCurrentLocation(고정확도, 10초, 캐시 불허)
├── capture/DeviceStateSnapshot.kt          수신 시점의 권한, 전원, 프로세스 상태
├── parse/TossNotificationParser.kt         ★ 파싱 규칙 (이 파일만 고치면 됨)
├── data/                                   Room(payment_record 단일 테이블), DataStore 설정
├── export/CsvBuilder.kt, CsvShare.kt       CSV 생성과 공유
├── util/Geo.kt, TimeFormat.kt, Permissions.kt
└── ui/                                     목록, 상세, 권한, 설정 (Compose, Material 3)
```

### 파서 수정
`TossNotificationParser.kt` 상단의 "규칙" 영역(정규식, 키워드)을 고치고 `VERSION`을 올립니다. 실제 알림 원문은 상세 화면이나 CSV의 `title/text/sub_text/big_text/extras_dump`에서 확인할 수 있습니다. 새 형식을 발견하면 `TossNotificationParserTest`에 케이스를 추가하세요.

### 수집하지 않는 알림
- **진행 중(ongoing) 알림**은 기록하지 않습니다. 토스 만보기 Live Activity처럼 상태가 바뀔 때마다 다시 올라오는 알림 때문에 기록과 위치 요청이 폭주하기 때문입니다.
- **그룹 요약 알림**(내용 없이 알림을 묶어 보여 주는 것)은 기록하되 위치는 측정하지 않습니다.
- 포인트 적립 같은 결제가 아닌 토스 알림은 기록과 위치 측정은 하지만, `parsed_kind`로 구분해 목록의 "실결제" 탭에서 제외합니다.

### 알려진 한계
- 상시 위치 추적은 하지 않습니다. 알림 1건당 1회만 측정합니다.
- 위치용 포그라운드 서비스는 쓰지 않습니다. 순수하게 백그라운드 위치 권한만으로 얼마나 되는지를 측정하기 위해서입니다.
- 앱을 삭제하면 데이터가 사라집니다. 정기적으로 CSV를 내보내 두세요.
