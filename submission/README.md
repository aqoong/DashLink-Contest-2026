# DashLink 시연 자료

제출 앱은 **0.3.8-mvp / versionCode 10**입니다. 현재 홈 화면과 기능별 대표 영상을 아래에서 확인할 수 있습니다. 영상은 이전 버전에서 촬영했으므로 현재 APK와 일부 화면 배치가 다릅니다. 실제 기능 확인에는 제출 APK와 [실행 안내](../README.md)를 사용해 주세요.

## APK와 화면

- [서명된 release APK](../apk/한원우_DashLink_release.apk)
- [현재 홈 화면 — 0.3.8](screenshots/borderless/01-home.png)
- [출력 방식 설정 — 0.3.5](screenshots/settings/02-output-settings.png)
- [차량 정보 상세 — 0.3.6](screenshots/sections/03-detail.png)
- [SOS 요청 — 0.3.1](screenshots/04-sos-requests.png)
- [메시지 진행과 빨간 중지 버튼 — 0.3.4](screenshots/home/02-red-stop.png)
- [주행 제한 — 0.3.4](screenshots/home/05-driving-block.png)
- [정차 후 복구 확인 — 0.3.4](screenshots/home/06-recovered.png)

## 대표 영상

| 영상 | 촬영 버전 | 확인할 내용 |
| --- | --- | --- |
| [사고 대응 시연](DashLink-demo.mp4) · [자막](DashLink-demo.srt) | 0.3.1 | 초기 설정, 문·타이어·배터리·위치 조회, 통신 두절 모의 사고, 10초 응답 대기와 SOS 요청 |
| [홈에서 메시지 보내기](DashLink-home.mp4) · [자막](DashLink-home.srt) | 0.3.4 | OK·고마워·미안해, 점·선 진행 표시, 같은 빨간 버튼으로 중지 |
| [주행 제한과 정차 후 복구](DashLink-recovery.mp4) · [자막](DashLink-recovery.srt) | 0.3.2 | Drive 전환 시 중단, Park 복귀 후 잠금 유지, 사용자 복구 요청과 원래 값 재조회 |

세 영상은 Pleos 에뮬레이터 화면을 H.264 / 1920×1080으로 저장한 자료입니다. 한국어 자막을 포함하며 별도 SRT도 제공합니다. 시험용 연락처와 좌표를 사용했습니다. 119·보호자 결과는 모의 처리이며, 조명 SDK 요청 응답은 물리 램프 점멸의 증거가 아닙니다. 확인 범위는 [검증 결과](../docs/validation.md)에 정리했습니다.

## 파일 확인과 접수

저장소 루트에서 `shasum -a 256 -c submission/SHA256SUMS.txt`를 실행하면 APK와 시연 자료의 일치 여부를 확인할 수 있습니다.

[공식 제출 안내](https://appdevchallenge.auto-hmg.io/guide/submission)에 따라 소스와 release APK를 함께 제공합니다. 접수 메일에는 저장소 주소와 최종 커밋 번호를 기재하며, 심사는 해당 커밋을 기준으로 진행됩니다. 비공개 제출 시에는 **2026년 11월 16일까지 공개로 전환하고 11월 23일까지 유지**해야 개발 산출물 가산점에 반영됩니다. 이 저장소는 우선 비공개로 준비했으며 공개 전환과 접수는 별도입니다. 참가신청서·개인정보 동의서는 저장소에 포함하지 않습니다.
